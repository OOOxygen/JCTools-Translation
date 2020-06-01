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


/**
 * 该类的实现有点像{@link MpmcArrayQueue}和{@link MpscLinkedQueue}的结合体。
 * <p>
 * An MPMC array queue which grows unbounded in linked chunks.<br>
 * Differently from {@link MpmcArrayQueue} it is designed to provide a better scaling when more
 * producers are concurrently offering.<br>
 * Users should be aware that {@link #poll()} could spin while awaiting a new element to be available:
 * to avoid this behaviour {@link #relaxedPoll()} should be used instead, accounting for the semantic differences
 * between the twos.
 *
 * @author https://github.com/franz1981
 */
public class MpmcUnboundedXaddArrayQueue<E> extends MpUnboundedXaddArrayQueue<MpmcUnboundedXaddChunk<E>, E>
{

    /**
     * @param chunkSize The buffer size to be used in each chunk of this queue
     * @param maxPooledChunks The maximum number of reused chunks kept around to avoid allocation, chunks are pre-allocated
     */
    public MpmcUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        super(chunkSize, maxPooledChunks);
    }

    public MpmcUnboundedXaddArrayQueue(int chunkSize)
    {
        this(chunkSize, 2);
    }

    @Override
    final MpmcUnboundedXaddChunk<E> newChunk(long index, MpmcUnboundedXaddChunk<E> prev, int chunkSize, boolean pooled)
    {
        return new MpmcUnboundedXaddChunk(index, prev, chunkSize, pooled);
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

        final long pIndex = getAndIncrementProducerIndex();

        // 分别为：pIndex落在要填充的chunk的哪个槽位，pIndex应该填充的chunk的索引（应该填充哪个编号的chunk）
        final int piChunkOffset = (int) (pIndex & chunkMask);
        final long piChunkIndex = pIndex >> chunkShift;

        MpmcUnboundedXaddChunk<E> pChunk = lvProducerChunk();
        if (pChunk.lvIndex() != piChunkIndex)
        {
            // 期望的chunkIndex和当前的chunk不同，
            // 表示可能需要创建新的chunk，或有其它生产者创建了新的chunk，而当前线程需要后跳到前面的chunk。

            // Other producers may have advanced the producer chunk as we claimed a slot in a prev chunk, or we may have
            // now stepped into a brand new chunk which needs appending.
            pChunk = producerChunkForIndex(pChunk, piChunkIndex);
        }

        final boolean isPooled = pChunk.isPooled();

        if (isPooled)
        {
            // 如果缓存池中的chunk，由于消费者可能提前归还chunk（尚未完全消费），因此必须等待该槽位的消费者完成消费
            // wait any previous consumer to finish its job
            pChunk.spinForElement(piChunkOffset, true);
        }
        pChunk.soElement(piChunkOffset, e);
        if (isPooled)
        {
            // 注意：对于缓存池中的chunk，先发布元素，再发布sequence（chunk编号）
            // 因此消费者必须等待sequence变为期望值之后才可以消费，sequence是生产者与消费者之间交互的关键 - 同MpmcArrayQueue。
            pChunk.soSequence(piChunkOffset, piChunkIndex);
        }
        return true;
    }

    @Override
    public E poll()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long cIndex;
        MpmcUnboundedXaddChunk<E> cChunk;
        int ciChunkOffset;
        boolean isFirstElementOfNewChunk;
        boolean pooled = false;
        E e = null;
        MpmcUnboundedXaddChunk<E> next = null;
        long pIndex = -1; // start with bogus value, hope we don't need it
        long ciChunkIndex;
        while (true)
        {
            isFirstElementOfNewChunk = false;

            // 无论谁先加载都会导致不一致的情况，因此必须校验。
            // chunk与index同步，并且在cas更新index之后安全的变更，因为我们提前验证了它与index指示的ciChunkIndex匹配 - index先更新，chunk后更新。

            cIndex = this.lvConsumerIndex();
            // chunk is in sync with the index, and is safe to mutate after CAS of index (because we pre-verify it
            // matched the indicate ciChunkIndex)
            cChunk = this.lvConsumerChunk();

            // 分别为：cIndex落在要消费的chunk的第几个槽，cIndex应该消费哪个索引的chunk（哪个编号的chunk）
            ciChunkOffset = (int) (cIndex & chunkMask);
            ciChunkIndex = cIndex >> chunkShift;

            final long ccChunkIndex = cChunk.lvIndex();
            if (ciChunkOffset == 0 && cIndex != 0) {
                // 走到这，表示首次（或重新）进入该块，可能需要切换当前消费的chunk
                if (ciChunkIndex - ccChunkIndex != 1)
                {
                    // 走到这表示有其它消费已经更新了chunk
                    continue;
                }
                isFirstElementOfNewChunk = true;

                // 走到这里，表示当前线程需要在队列不为空的情况下，尝试切换chunk
                // next可能已经被其它消费者线程修改，但是：
                // 如果next为null，我们无法确定是其它消费者已清理cChunk到next的链接，还是队列为空，因此必须检查队列是否为空，并尝试cas
                // 如果next不为null，那么就需要CAS竞争索引，CAS成功的消费者负责切换chunk

                next = cChunk.lvNext();
                // next could have been modified by another racing consumer, but:
                // - if null: it still needs to check q empty + casConsumerIndex
                // - if !null: it will fail on casConsumerIndex
                if (next == null)
                {
                    if (cIndex >= pIndex && // test against cached pIndex
                        cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                    {
                        // 严格的空检查，以满足Queue对poll的语义要求（当且仅当队列为空时才能返回null）
                        // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                        return null;
                    }
                    // 队列不为空，接下来进行CAS，并且成功的那个消费者赋值切换（更新）chunk
                    // we will go ahead with the CAS and have the winning consumer spin for the next buffer
                }

                // 到这里，表示队列不为空，或next不为null，需要CAS竞争更新索引，更新索引成功的消费者负责切换chunk
                // not empty: can attempt the cas (and transition to next chunk if successful)
                if (casConsumerIndex(cIndex, cIndex + 1))
                {
                    break;
                }
                continue;
            }

            if (ccChunkIndex > ciChunkIndex)
            {
                // chunk的index大于当前cIndex落在的chunk，在加载cIndex后，其它线程开始切换chunk，因而可能发生，从而产生一个旧视图。
                //stale view of the world
                continue;
            }

            // mid chunk elements
            assert !isFirstElementOfNewChunk && ccChunkIndex <= ciChunkIndex;
            pooled = cChunk.isPooled();
            if (ccChunkIndex == ciChunkIndex)
            {
                if (pooled)
                {
                    // sequence cIndex索引对应槽位的状态值（是否已填充，已消费）
                    // sequence == ciChunkIndex 表示该槽位已经被填充（填充之后赋值为chunkIndex），可以被消费（此时竞争更新消费者索引）
                    // sequence > ciChunkIndex  表示已经被消费，且被下一环的生产者填充了，此时需要重试
                    // sequence < ciChunkIndex  表示队列可能为空，或生产者正在填充，或已填充但sequence尚不可见，此时需要验证队列是否为空

                    // 池化的chunk需要比空检查更强的保证，以防止生产者在重用chunk时出现过时的视图。
                    // 在该（过时）视图中，一个消费者已获取该槽但尚未将其空出来（element不为null），而生产者尚未将其设置为新值 - 怎么看不懂了呢？？？
                    // 解释下：由于重用chunk，因此速度快的消费者可能追上消费慢的消费者！如果不校验sequence，速度快的消费者可能重复消费。

                    // Pooled chunks need a stronger guarantee than just element null checking in case of a stale view
                    // on a reused entry where a racing consumer has grabbed the slot but not yet null-ed it out and a
                    // producer has not yet set it to the new value.
                    final long sequence = cChunk.lvSequence(ciChunkOffset);
                    if (sequence == ciChunkIndex)
                    {
                        if (casConsumerIndex(cIndex, cIndex + 1))
                        {
                            break;
                        }
                        continue;
                    }
                    if (sequence > ciChunkIndex)
                    {
                        //stale view of the world
                        continue;
                    }
                    // sequence < ciChunkIndex: element yet to be set?
                }
                else
                {
                    // 如果不是缓存池中的chunk，如果element不为null，则表示当前槽位可消费，由CAS成功的线程消费。
                    e = cChunk.lvElement(ciChunkOffset);
                    if (e != null)
                    {
                        if (casConsumerIndex(cIndex, cIndex + 1))
                        {
                            break;
                        }
                        continue;
                    }
                    // e == null: element yet to be set?
                }
            }
            // ccChunkIndex < ciChunkIndex || e == null || sequence < ciChunkIndex:
            if (cIndex >= pIndex && // test against cached pIndex
                cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
            {
                // 严格的空检查，以满足Queue对poll的语义要求（当且仅当队列为空时才能返回null）
                // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                return null;
            }
        }

        // if we are the isFirstElementOfNewChunk we need to get the consumer chunk
        if (isFirstElementOfNewChunk)
        {
            // 如果是新chunk的第一个元素，则表示当前线程CAS竞争索引成功，需要负责更新chunk，并返回新chunk的第一个元素
            e = linkNextConsumerChunkAndPoll(cChunk, next, ciChunkIndex);
        }
        else
        {
            if (pooled)
            {
                // 缓存池中的chunk的元素，为什么不在CAS后加载，而在这里加载呢？可能是为了对应offer吧。
                // 如果是池中的chunk，上方只检查了sequence，并未加载，因此这里加载
                e = cChunk.lvElement(ciChunkOffset);
            }
            assert !cChunk.isPooled() ||  (cChunk.isPooled() && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);

            // 需要清理元素，对于缓存池中的chunk，生产者依赖于element为null，因此使用Ordered模式，以保证尽快的可见性
            cChunk.soElement(ciChunkOffset, null);
        }
        return e;
    }

    /**
     * Q: 这里是如何实现互斥的？
     * A: 与生产者类似，这里只允许CAS竞争cIndex成功的那个线程切换消费的chunk，其它线程必须等待chunk切换完成。
     * 在这期间，其它消费者无法在这期间增加cIndex。
     */
    private E linkNextConsumerChunkAndPoll(
        MpmcUnboundedXaddChunk<E> cChunk,
        MpmcUnboundedXaddChunk<E> next,
        long expectedChunkIndex)
    {
        // 传入的next参数可能为null。
        // 走到这，表示队列一定不为空，但此时next可能尚不可达，因此需要自旋直到next可见（看见生产者最新的chunk）。
        while (next == null)
        {
            next = cChunk.lvNext();
        }

        // 我们可以简单的自旋等待生产者，因为我们是为唯一负责旋转消费的buffer并使用next的线程 - 该方法是互斥的，只有一个线程能进入。
        // we can freely spin awaiting producer, because we are the only one in charge to
        // rotate the consumer buffer and use next
        final E e = next.spinForElement(0, false);

        final boolean pooled = next.isPooled();
        if (pooled)
        {
            // 这里必须保持和poll相同的语义保证：必须等待sequence为期望值时才可以消费。
            next.spinForSequence(0, expectedChunkIndex);
        }

        // next - 使用Ordered模式，因为生产者依赖于element为null，而不是sequence，因此需要保证尽快的可见性。
        // 注意：这里和MpmcArrayQueue不同，这里消费者并不需要更新sequence
        next.soElement(0, null);
        moveToNextConsumerChunk(cChunk, next);
        return e;
    }

    @Override
    public E peek()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        long cIndex;
        E e;
        do
        {
            e = null;
            cIndex = this.lvConsumerIndex();
            MpmcUnboundedXaddChunk<E> cChunk = this.lvConsumerChunk();
            final int ciChunkOffset = (int) (cIndex & chunkMask);
            final long ciChunkIndex = cIndex >> chunkShift;
            final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex != 0;
            if (firstElementOfNewChunk)
            {
                final long expectedChunkIndex = ciChunkIndex - 1;
                if (expectedChunkIndex != cChunk.lvIndex())
                {
                    continue;
                }
                final MpmcUnboundedXaddChunk<E> next = cChunk.lvNext();
                if (next == null)
                {
                    continue;
                }
                cChunk = next;
            }
            if (cChunk.isPooled())
            {
                if (cChunk.lvSequence(ciChunkOffset) != ciChunkIndex)
                {
                    continue;
                }
            } else {
                if (cChunk.lvIndex() != ciChunkIndex)
                {
                    continue;
                }
            }
            // 理论上这是有bug的，无法保证加载的元素对应cIndex，
            // 但是很难复现，因为需要在生产者恰好重用该chunk，而消费还未追上生产者，且上次peek的那个线程还未load完成时才会发生...
            e = cChunk.lvElement(ciChunkOffset);
        }
        while (e == null && cIndex != lvProducerIndex());
        return e;
    }

    @Override
    public E relaxedPoll()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        final long cIndex = this.lvConsumerIndex();
        final MpmcUnboundedXaddChunk<E> cChunk = this.lvConsumerChunk();

        // 分别为：cIndex落在要消费的chunk的第几个槽，cIndex应该消费哪个索引的chunk（哪个编号的chunk）
        final int ciChunkOffset = (int) (cIndex & chunkMask);
        final long ciChunkIndex = cIndex >> chunkShift;

        // 消费者是否是首次进入该chunk （除去第一个块）
        final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex != 0;
        if (firstElementOfNewChunk)
        {
            // 走到这，表示首次（或重新）进入该块，可能需要切换当前消费的chunk
            final long expectedChunkIndex = ciChunkIndex - 1;
            final MpmcUnboundedXaddChunk<E> next;
            final long ccChunkIndex = cChunk.lvIndex();
            if (expectedChunkIndex != ccChunkIndex || (next = cChunk.lvNext()) == null)
            {
                // expectedChunkIndex != ccChunkIndex 表示有其它消费已经更新了chunk - 这里也没有进行更多的努力(因为可能阻塞，需要等待其完成更新)
                // next == null 表示下一个chunk尚不可达
                return null;
            }
            E e = null;
            final boolean pooled = next.isPooled();
            if (pooled)
            {
                // 如果是缓存池中的chunk，只有当对应槽位的sequence为index表示的chunkIndex的时候才可以消费
                if (next.lvSequence(0) != ciChunkIndex)
                {
                    return null;
                }
            }
            else
            {
                // 当不是缓存池中的chunk时，根据element是否为null，即可界定是否可消费
                e = next.lvElement(0);
                if (e == null)
                {
                    return null;
                }
            }
            // 走到这里，表示下一个要消费的元素已经被填充，此时需要竞争CAS索引，竞争成功的才可以消费对应的元素
            if (!casConsumerIndex(cIndex, cIndex + 1))
            {
                return null;
            }
            if (pooled)
            {
                // 如果是池中的chunk，上方只检查了sequence，并未加载，因此这里加载
                e = next.lvElement(0);
            }
            assert e != null;

            // next - 使用Ordered模式，因为生产者依赖于element为null，而不是sequence，因此需要保证尽快的可见性。
            // 注意：这里和MpmcArrayQueue不同，这里消费者并不需要更新sequence
            next.soElement(0, null);
            moveToNextConsumerChunk(cChunk, next);
            return e;
        }
        else
        {
            // 走到这，表示要消费的chunk中的中间段（非head）
            final boolean pooled = cChunk.isPooled();
            E e = null;
            if (pooled)
            {
                // 如果是缓存池中的chunk，只有当对应槽位的sequence为index表示的chunkIndex的时候才可以消费
                // 这里其实隐式地校验了cChunk的index
                final long sequence = cChunk.lvSequence(ciChunkOffset);
                if (sequence != ciChunkIndex)
                {
                    return null;
                }
            }
            else
            {
                // 当不是缓存池中的chunk时，根据element是否为null，即可界定是否可消费
                final long ccChunkIndex = cChunk.lvIndex();
                if (ccChunkIndex != ciChunkIndex || (e = cChunk.lvElement(ciChunkOffset)) == null)
                {
                    // 不是当前索引对应的chunk，或element尚不可见
                    return null;
                }
            }
            // 走到这，表示对应的槽位可消费，需要CAS竞争索引，成功的线程负责消费
            if (!casConsumerIndex(cIndex, cIndex + 1))
            {
                return null;
            }
            if (pooled)
            {
                // 如果是池中的chunk，上方只检查了sequence，并未加载，因此这里加载
                e = cChunk.lvElement(ciChunkOffset);
                assert e != null;
            }
            assert !pooled || (pooled && cChunk.lvSequence(ciChunkOffset) == ciChunkIndex);
            // next - 使用Ordered模式，因为生产者依赖于element为null，而不是sequence，因此需要保证尽快的可见性。
            cChunk.soElement(ciChunkOffset, null);
            return e;
        }
    }

    @Override
    public E relaxedPeek()
    {
        final int chunkMask = this.chunkMask;
        final int chunkShift = this.chunkShift;
        final long cIndex = this.lvConsumerIndex();
        final int ciChunkOffset = (int) (cIndex & chunkMask);
        final long ciChunkIndex = cIndex >> chunkShift;

        MpmcUnboundedXaddChunk<E> consumerBuffer = this.lvConsumerChunk();

        final int chunkSize = chunkMask + 1;
        final boolean firstElementOfNewChunk = ciChunkOffset == 0 && cIndex >= chunkSize;
        if (firstElementOfNewChunk)
        {
            final long expectedChunkIndex = ciChunkIndex - 1;
            if (expectedChunkIndex != consumerBuffer.lvIndex())
            {
                return null;
            }
            final MpmcUnboundedXaddChunk<E> next = consumerBuffer.lvNext();
            if (next == null)
            {
                return null;
            }
            consumerBuffer = next;
        }
        if (consumerBuffer.isPooled())
        {
            if (consumerBuffer.lvSequence(ciChunkOffset) != ciChunkIndex)
            {
                return null;
            }
        }
        else
        {
            if (consumerBuffer.lvIndex() != ciChunkIndex)
            {
                return null;
            }
        }
        // 理论上这是有bug的，无法保证加载的元素对应cIndex，
        // 但是很难复现，因为需要在生产者恰好重用该chunk，而消费还未追上生产者，且上次peek的那个线程还未load完成时才会发生...
        return consumerBuffer.lvElement(ciChunkOffset);
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
        long producerSeq = getAndAddProducerIndex(limit);
        MpmcUnboundedXaddChunk<E> producerBuffer = null;
        for (int i = 0; i < limit; i++)
        {
            final int pOffset = (int) (producerSeq & chunkMask);
            long chunkIndex = producerSeq >> chunkShift;
            if (producerBuffer == null || producerBuffer.lvIndex() != chunkIndex)
            {
                producerBuffer = producerChunkForIndex(producerBuffer, chunkIndex);
                if (producerBuffer.isPooled())
                {
                    chunkIndex = producerBuffer.lvIndex();
                }
            }
            if (producerBuffer.isPooled())
            {
                while (producerBuffer.lvElement(pOffset) != null)
                {

                }
            }
            producerBuffer.soElement(pOffset, s.get());
            if (producerBuffer.isPooled())
            {
                producerBuffer.soSequence(pOffset, chunkIndex);
            }
            producerSeq++;
        }
        return limit;
    }

}
