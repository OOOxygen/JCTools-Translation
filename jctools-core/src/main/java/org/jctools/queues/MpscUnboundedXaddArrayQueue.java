/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jctools.queues;

import org.jctools.util.PortableJvmInfo;

/**
 * 以链接的chunk无限增量的Mpsc数组队列。
 * 与{@link MpscUnboundedArrayQueue}不同的是，该类的设计目标是在多个生产者并发offer时提供更好的可伸缩性。
 * 用户需要知道{@link #poll()}可能在等待新元素可用时自旋（阻塞），为了避免这种行为，应该使用{@link #relaxedPoll()}，
 * 这也是两者之间的语义差异。
 * <p>
 * Q: 为什么该类的吞吐量远高于{@link MpscUnboundedArrayQueue}？
 * A: 虽然{@link MpscUnboundedArrayQueue}也是无界队列，但它是伪无界队列，仍然考虑了消费者进度(节省空间)，因此产生了许多额外的竞争。
 * 而这里的实现更像{@link MpscLinkedQueue}，只需要处理生产者之间的竞争，不再考虑消费者进度，空间换时间，因而能大大提高吞吐量。
 * <p>
 * 该类的实现还是较为简单的，没有特别复杂的逻辑，包括实现chunk缓存池也很简单，因为是消费完chunk才归还给池，因此很容易保证正确性。
 *
 * An MPSC array queue which grows unbounded in linked chunks.<br>
 * Differently from {@link MpscUnboundedArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.<br>
 * Users should be aware that {@link #poll()} could spin while awaiting a new element to be available:
 * to avoid this behaviour {@link #relaxedPoll()} should be used instead, accounting for the semantic differences
 * between the twos.
 *
 * @author https://github.com/franz1981
 */
public class MpscUnboundedXaddArrayQueue<E> extends MpUnboundedXaddArrayQueue<MpscUnboundedXaddChunk<E>, E>
{
    /**
     * @param chunkSize The buffer size to be used in each chunk of this queue
     * @param maxPooledChunks The maximum number of reused chunks kept around to avoid allocation, chunks are pre-allocated
     */
    public MpscUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        super(chunkSize, maxPooledChunks);
    }

    public MpscUnboundedXaddArrayQueue(int chunkSize)
    {
        this(chunkSize, 2);
    }

    @Override
    final MpscUnboundedXaddChunk<E> newChunk(long index, MpscUnboundedXaddChunk<E> prev, int chunkSize, boolean pooled)
    {
        return new MpscUnboundedXaddChunk(index, prev, chunkSize, pooled);
    }

    @Override
    public boolean offer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;

        // 注意这里的时序：生产者先更新索引，再发布元素
        final long pIndex = getAndIncrementProducerIndex();

        // 分别为：pIndex落在要填充的chunk的哪个槽位，pIndex应该填充的chunk的索引（应该填充哪个编号的chunk）
        final int piChunkOffset = (int) (pIndex & chunkMask);
        final long piChunkIndex = pIndex >> chunkShift;

        MpscUnboundedXaddChunk<E> pChunk = lvProducerChunk();
        if (pChunk.lvIndex() != piChunkIndex)
        {
            // 期望的chunkIndex和当前的chunk不同，
            // 表示可能需要创建新的chunk，或有其它生产者创建了新的chunk，而当前线程需要后跳到前面的chunk。

            // Other producers may have advanced the producer chunk as we claimed a slot in a prev chunk, or we may have
            // now stepped into a brand new chunk which needs appending.
            pChunk = producerChunkForIndex(pChunk, piChunkIndex);
        }
        // 找到期望的chunk，可直接填充
        pChunk.soElement(piChunkOffset, e);
        return true;
    }

    /**
     * 当前chunk已消费完毕，此时需要从下一个chunk消费。
     * 注意：该方法在有可消费的下一个chunk的情况下会切换chunk，多次调用可能切换多个chunk。
     *
     * @param cChunk 当前chunk
     * @param cIndex 当前消费者索引
     * @return 如果队列为空（消费索引等于生产者索引）则返回null，否则返回下一个chunk
     */
    private MpscUnboundedXaddChunk<E> pollNextBuffer(MpscUnboundedXaddChunk<E> cChunk, long cIndex)
    {
        final MpscUnboundedXaddChunk<E> next = spinForNextIfNotEmpty(cChunk, cIndex);

        if (next == null)
        {
            return null;
        }

        moveToNextConsumerChunk(cChunk, next);
        assert next.lvIndex() == cIndex >> chunkShift;
        return next;
    }

    /**
     * 自旋等待下一个chunk，当前chunk已消费完毕，此时需要从下一个chunk消费。
     *
     * @param cChunk 当前chunk
     * @param cIndex 当前消费者索引
     * @return 如果队列为空（消费索引等于生产者索引）则返回null，否则返回下一个chunk
     */
    private MpscUnboundedXaddChunk<E> spinForNextIfNotEmpty(MpscUnboundedXaddChunk<E> cChunk, long cIndex)
    {
        MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
        if (next == null)
        {
            // 队列为空
            if (lvProducerIndex() == cIndex)
            {
                return null;
            }

            // 队列不为空，那么自旋一定能等待下一个chunk（生产者会在稍后发布它）
            do
            {
                next = cChunk.lvNext();
            }
            while (next == null);
        }
        return next;
    }

    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();

        // cIndex落在要消费的chunk的哪个槽位
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.lvConsumerChunk();
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            // ciChunkOffset == 0 表示消费chunk的第一个元素
            // cIndex != 0 表示不是第一个chunk
            // 走到这，表示将要消费一个新的chunk（需要跳转到下一个chunk）

            // pollNextBuffer will verify emptiness check
            cChunk = pollNextBuffer(cChunk, cIndex);
            if (cChunk == null)
            {
                return null;
            }
        }

        // 走到这，表示当前chunk就是要消费的chunk
        E e = cChunk.lvElement(ciChunkOffset);
        if (e == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                // 队列为空 - 这里可能是一个既有的chunk，因此和上面的pollNextBuffer的空检查无关
                return null;
            }
            else
            {
                // 队列不为空，自旋等待元素可见
                e = cChunk.spinForElement(ciChunkOffset, false);
            }
        }
        // 注意这里的时序：先清理元素，再更新索引
        // 理论上可以使用Plain模式清理元素，因为它在归还chunk到池，生产者再从池中取出时一定都已清空（offer和poll之间具备happens-before关系）
        cChunk.soElement(ciChunkOffset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.lpConsumerChunk();
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            // 走到这，表示将要消费一个新的chunk（需要跳转到下一个chunk）

            // Q: 为什么不调用pollNextBuffer切换到下一个chunk？
            // A: 因为peek不会导致cIndex改变，那么下一次peek还是会进入该方法，而在该类的实现中，是没有检查chunk的index的，
            // 而每次调用pollNextBuffer都可能导致chunk切换，从而导致消费者丢失未消费的数据。

            cChunk = spinForNextIfNotEmpty(cChunk, cIndex);
            if (cChunk == null)
            {
                return null;
            }
        }

        // 走到这，表示该chunk是我们正在消费或下一次消费的chunk
        E e = cChunk.lvElement(ciChunkOffset);
        if (e == null)
        {
            if (lvProducerIndex() == cIndex)
            {
                // 队列为空 - 这里可能是一个既有的chunk，因此和上面的spinForNextIfNotEmpty的空检查无关
                return null;
            }
            else
            {
                // 队列不为空，自旋等待元素可见
                e = cChunk.spinForElement(ciChunkOffset, false);
            }
        }
        return e;
    }

    @Override
    public E relaxedPoll()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.lpConsumerChunk();
        E e;
        // start of new chunk?
        if (ciChunkOffset == 0 && cIndex != 0)
        {
            // 走到这，表示将要消费一个新的chunk
            final MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
            if (next == null)
            {
                // 队列为空或下一个chunk尚不可见，因为是relaxedPoll，因此可以直接返回null
                return null;
            }
            // 注意：因为是多生产者，因此可能第一个元素尚不可见，但后面的元素已经可见，
            // 但是这里是relaxedPoll，允许我们在队列不为空的情况下返回null，因此我们不检查生产者索引，
            // 我们只需要检查第一个元素（下一个要消费的元素）是否可以消费，可以消费则切换到下一个chunk并消费，否则直接返回null。

            e = next.lvElement(0);

            // if the next chunk doesn't have the first element set we give up
            if (e == null)
            {
                return null;
            }
            // 下一个chunk可以使用了（注意：该类的策略是可以poll的时候才切换chunk）
            moveToNextConsumerChunk(cChunk, next);

            cChunk = next;
        }
        else
        {
            // 走到这，表示消费既有的chunk，只要element不为null即可消费
            e = cChunk.lvElement(ciChunkOffset);
            if (e == null)
            {
                return null;
            }
        }

        // 与poll时序一致，注意这里的时序：先清理元素，再更新索引
        // 同样的，这里也可以使用Plain模式清理元素
        cChunk.soElement(ciChunkOffset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        final int chunkMask = this.chunkMask;
        final long cIndex = this.lpConsumerIndex();
        final int cChunkOffset = (int) (cIndex & chunkMask);

        MpscUnboundedXaddChunk<E> cChunk = this.lpConsumerChunk();

        // start of new chunk?
        if (cChunkOffset == 0 && cIndex !=0)
        {
            // 和peek相同，这里也不可以切换chunk - 该类的消费策略不依赖于chunkIndex
            cChunk = cChunk.lvNext();
            if (cChunk == null)
            {
                // 队列为空或下一个chunk尚不可见，因为是relaxedPeek，因此可以直接返回null
                return null;
            }
        }
        // 从当前chunk获取元素
        return cChunk.lvElement(cChunkOffset);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = chunkMask + 1;
        final int offerBatch = Math.min(PortableJvmInfo.RECOMENDED_OFFER_BATCH, capacity);
        do
        {
            // 做了小小的优化，每次尝试填充至多一个chunk
            final int filled = fill(s, offerBatch);
            if (filled == 0)
            {
                return (int) result;
            }
            result += filled;
        }
        while (result <= capacity);
        // 至少填了一个chunk的数据
        return (int) result;
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        if (null == c)
            throw new IllegalArgumentException("c is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative: " + limit);
        if (limit == 0)
            return 0;

        final int chunkMask = this.chunkMask;

        long cIndex = this.lpConsumerIndex();

        MpscUnboundedXaddChunk<E> cChunk = this.lpConsumerChunk();

        // 整体而言，循环内逻辑和relaxedPoll是相同的
        for (int i = 0; i < limit; i++)
        {
            final int consumerOffset = (int) (cIndex & chunkMask);
            E e;
            if (consumerOffset == 0 && cIndex != 0)
            {
                // 走到这，表示将要消费一个新的chunk
                final MpscUnboundedXaddChunk<E> next = cChunk.lvNext();
                if (next == null)
                {
                    // 队列为空或下一个chunk尚不可见，由于语义上等于relaxedPoll调用，因此可以直接返回null
                    return i;
                }
                // 注意：因为是多生产者，因此可能第一个元素尚不可见，但后面的元素已经可见，
                // 但是这里是relaxedPoll，允许我们在队列不为空的情况下返回null，因此我们不检查生产者索引，
                // 我们只需要检查第一个元素（下一个要消费的元素）是否可以消费，可以消费则切换到下一个chunk并消费，否则直接返回null。

                e = next.lvElement(0);

                // if the next chunk doesn't have the first element set we give up
                if (e == null)
                {
                    return i;
                }
                // 只有可以消费下一个chunk的时候，才切换chunk
                moveToNextConsumerChunk(cChunk, next);

                cChunk = next;
            }
            else
            {
                // 走到这，表示消费既有的chunk，只要element不为null即可消费
                e = cChunk.lvElement(consumerOffset);
                if (e == null)
                {
                    return i;
                }
            }

            // 时序与poll相同，先清理元素，再更新索引 - 其实我并不喜欢 ++cIndex 这种写法，容易导致错误，但是不用++cIndex，会让人觉得冗余...
            // 同样的，这里可以使用Plain模式清理元素
            cChunk.soElement(consumerOffset, null);
            final long nextConsumerIndex = cIndex + 1;
            soConsumerIndex(nextConsumerIndex);

            c.accept(e);
            cIndex = nextConsumerIndex;
        }
        return limit;
    }

    @Override
    public int fill(Supplier<E> s, int limit)
    {
        if (null == s)
            throw new IllegalArgumentException("supplier is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative:" + limit);
        if (limit == 0)
            return 0;

        final int chunkShift = this.chunkShift;
        final int chunkMask = this.chunkMask;

        // 这里申请了limit个空间，返回值为add前的pIndex
        // 其它逻辑和offer就相同了
        long pIndex = getAndAddProducerIndex(limit);
        MpscUnboundedXaddChunk<E> pChunk = null;
        for (int i = 0; i < limit; i++)
        {
            // 分别为：pIndex落在要填充的chunk的哪个槽位，pIndex应该填充的chunk的索引（应该填充哪个编号的chunk）
            final int pChunkOffset = (int) (pIndex & chunkMask);
            final long chunkIndex = pIndex >> chunkShift;

            if (pChunk == null || pChunk.lvIndex() != chunkIndex)
            {
                // 期望的chunkIndex和当前的chunk不同，
                // 表示可能需要创建新的chunk，或有其它生产者创建了新的chunk，而当前线程需要后跳到前面的chunk。
                pChunk = producerChunkForIndex(pChunk, chunkIndex);
            }

            // 使用ordered模式确保安全发布
            // 注意Supplier对get方法的约束 - 不可抛出异常，不可返回null，否则队列将被永久破坏（断开），不可恢复。
            pChunk.soElement(pChunkOffset, s.get());
            pIndex++;
        }
        return limit;
    }
}
