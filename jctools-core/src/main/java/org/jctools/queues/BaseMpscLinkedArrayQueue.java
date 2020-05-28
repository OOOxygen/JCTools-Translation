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

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.NoSuchElementException;

import static org.jctools.queues.LinkedArrayQueueUtil.length;
import static org.jctools.queues.LinkedArrayQueueUtil.modifiedCalcCircularRefElementOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;


abstract class BaseMpscLinkedArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    /**
     * 缓存行填充，避免{@code producerIndex}上产生伪共享
     */
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueProducerFields<E> extends BaseMpscLinkedArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueProducerFields.class, "producerIndex");

    /**
     * 生产者索引 - 由于是多生产者，因此是先竞争更新索引，再填充元素。
     * 注意：真正的索引其实是{@code producerIndex >> 1}，最低位用于表示队列的状态（是否正在调整大小）。
     * 将队列的状态存储在索引中，这样可以避免读取多个字段，以及并发更新问题。
     * PS: {@link java.util.concurrent.ThreadPoolExecutor}也有相似设计。
     * 我有个想法：最高位存储标记会不会更好？可以减少特殊情况。
     */
    private volatile long producerIndex;

    /**
     * loadVolatileProducerIndex
     * 多生产者模式下都必须使用volatile模式加载最新值
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * storeOrderedProducerIndex
     * 当某个线程获得唯一修改权期间（resize期间），可以使用该模式更新索引。
     * 其实我觉得应该用volatile模式写，保证对其它线程的立即可见性，否则其它线程会有较长时间的自旋。
     */
    final void soProducerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    /**
     * 生产者竞争索引，成功那个可以填充对应的槽位
     */
    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class BaseMpscLinkedArrayQueuePad2<E> extends BaseMpscLinkedArrayQueueProducerFields<E>
{
    /**
     * 缓存行填充，避免{@code consumerIndex}和{@code producerIndex}上产生伪共享。
     */
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueConsumerFields<E> extends BaseMpscLinkedArrayQueuePad2<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueConsumerFields.class,"consumerIndex");

    /**
     * 消费者索引
     * 因为生产者依赖于消费者的索引，因此消费者需要先清除元素再更新索引，以确保不会产生清除掉生产者填充的数据（不会并发修改同一槽位）。
     */
    private volatile long consumerIndex;
    /**
     * 消费当前消费的数组的掩码
     */
    protected long consumerMask;
    /**
     * 消费者当前消费的数组
     */
    protected E[] consumerBuffer;

    /**
     * loadVolatileConsumerIndex
     * 非消费者线程使用该方法读取
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * loadPlainConsumerIndex
     * 消费者使用该方法读取即可
     */
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    /**
     * storeOrderedConsumerIndex
     * 消费者使用该方法更新索引，以确保原子存储，且对生产者的可见性，此外还保证了槽位上不会发生并发更新。
     */
    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class BaseMpscLinkedArrayQueuePad3<E> extends BaseMpscLinkedArrayQueueConsumerFields<E>
{
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

// $gen:ordered-fields
abstract class BaseMpscLinkedArrayQueueColdProducerFields<E> extends BaseMpscLinkedArrayQueuePad3<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(BaseMpscLinkedArrayQueueColdProducerFields.class,"producerLimit");

    /**
     * 在重新读取消费者索引之前，第一个不可用的生产者索引。
     * <p>
     * Q: 这部分数据需要与{@code producerIndex}分开吗？
     * A: 因为是多生产模式，因此producerIndex上将产生高度竞争，因此其所在的缓存行极易失效，
     * 将这部分数据与producerIndex分开，我们期望该这部分数据大部分时间位于用于共享（且很少失效）的缓存行中。
     * PS: 这也隔得太远了，为啥不把消费者的放最上面？
     */
    private volatile long producerLimit;
    /**
     * 生产者当前使用的数组的掩码
     * 注意：用{@code producerIndex}一样，都进行了左移，最低位始终为0。
     * <p>
     * Q: 为啥mask为{@code (buffer.length - 2) << 1}?
     * A: 数组的真实长度为2的n次幂+1，数组最后一个元素用于存储到下一个数组的索引，因此有{@code buffer.length-2}，
     * 左移一位是为了对齐{@code producerIndex}，因为生产者索引的最低位用于表示当前是否正在进行resize（扩容）。
     */
    protected long producerMask;
    /**
     * 生产者当前使用的数组 - 在resize时会更新。
     */
    protected E[] producerBuffer;

    /**
     * loadVolatileProducerLimit
     * 多生产者模式下，都必须使用volatile模式读取
     */
    final long lvProducerLimit()
    {
        return producerLimit;
    }

    /**
     * 在其它队列实现中，很少有CAS更新producerLimit的，因为一般情况下缓存一个旧值并没有太大问题。
     * Q: 为什么需要CAS更新？
     * A: 需要保证producerLimit和producerMask和producerBuffer对应，即producerLimit必须落在当前数组上。
     * 如果可能落在不同的数组上，如果只根据pIndex < producerLimit，我们并不能采取下一步行动。
     */
    final boolean casProducerLimit(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_LIMIT_OFFSET, expect, newValue);
    }

    /**
     * storeOrderedProducerLimit
     * 在该队列实现中，只有resize的线程调用该方法，即有独占权时可以使用该方法更新。
     */
    final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}


/**
 * 多生产者单消费者模式的基于LinkedArray的队列，不难想到，复杂度主要在生产者竞争扩容。
 * 这里没有进行后向填充，子类需要处理后向填充问题。
 * <p>
 * 该类是一个模板实现，并提供了钩子方法供子类扩展。
 *
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks
 * of the initial size. The queue grows only when the current buffer is full and elements are not copied on
 * resize, instead a link to the new buffer is stored in the old buffer for the consumer to follow.
 */
abstract class BaseMpscLinkedArrayQueue<E> extends BaseMpscLinkedArrayQueueColdProducerFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    // No post padding here, subclasses must add
    /**
     * 跳点标记，表示当前索引对应的元素在下一个数组。
     * 对于生产者而言，表示resize已完成，大家需要到下一个数组竞争索引和填充元素。
     * 对于消费者而言，表示当前数组已消费完成，需要跳转到下一个数组进行消费。
     */
    private static final Object JUMP = new Object();
    /**
     * 标记当前数组已被完全消费 - 用于区分队列已空还是当前数组已断开连接，这是引入{@link WeakIterator}导致的。
     */
    private static final Object BUFFER_CONSUMED = new Object();

    /**
     * 跳转到CAS竞争索引代码块
     */
    private static final int CONTINUE_TO_P_INDEX_CAS = 0;
    /**
     * 简单重试(continue)
     */
    private static final int RETRY = 1;
    /**
     * 队列已满
     */
    private static final int QUEUE_FULL = 2;
    /**
     * 获得resize权（此时其它生产者需要等待resize完成）
     */
    private static final int QUEUE_RESIZE = 3;


    /**
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *                        Must be 2 or more.
     */
    public BaseMpscLinkedArrayQueue(final int initialCapacity)
    {
        RangeUtil.checkGreaterThanOrEqual(initialCapacity, 2, "initialCapacity");

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        // leave lower bit of mask clear
        long mask = (p2capacity - 1) << 1;
        // need extra element to point at next array
        E[] buffer = allocateRefArray(p2capacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        soProducerLimit(mask); // we know it's all empty to start with
    }

    @Override
    public int size()
    {
        // 该算法简化一下，如下：
        // long cIndex = lvConsumerIndex();
        // long size = (lvProducerIndex() - cIndex) >> 1;

        // 提示：因为索引都是偶数，因此不能使用IndexedQueueSizeUtil (其实生产者索引可能为奇数，严格的说是索引都是位移后的，因此不能直接使用工具类计算)
        // 由于当前线程在读取producerIndex和consumerIndex期间可能被中断或被重新调度，因此我们需要保证size在有效范围。
        // 在当前方法计算size的时候，并发的poll/offer事件是可能的，因此在读取producerIndex之前读取consumerIndex。

        // NOTE: because indices are on even numbers we cannot use the size util.

        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = lvConsumerIndex();
        long size;
        while (true)
        {
            final long before = after;
            final long currentProducerIndex = lvProducerIndex();
            after = lvConsumerIndex();
            if (before == after)
            {
                // 走到这，意味着这期间没有消费者消费，size就更精确，不过实际意义不大
                size = ((currentProducerIndex - after) >> 1);
                break;
            }
        }
        // Long overflow is impossible, so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        else
        {
            return (int) size;
        }
    }

    @Override
    public boolean isEmpty()
    {
        // 顺序很重要！
        // 先读取consumerIndex再读取producerIndex，允许了生产者在加载consumerIndex之后增加procuderIndex，
        // 这样可以保证该方法的估算值是保守的。
        // 注意，对于MPMC，我们无法做任何事情来使其成为精确的方法。

        // 注意：我们的生产者索引是可能为奇数的，在resize的时候！
        // 此时虽然索引不等，但是队列其实是空的，在完成扩容之后，生产者才会真正竞争下一个索引和填充元素。

        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (this.lvConsumerIndex() == this.lvProducerIndex());
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        long mask;
        E[] buffer;
        long pIndex;

        while (true)
        {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1)
            {
                // 最后1位为1，表示有生产者正在扩容，此时需要等待扩容完毕
                continue;
            }
            // pIndex为偶数（低位为0） -> 真实的索引为 pIndex >> 1
            // mask和buffer可能因为某个生产者扩容导致改变 -> 仅用于CAS成功后的数组访问

            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            mask = this.producerMask;
            buffer = this.producerBuffer;

            // 一个成功的CAS建立了特定时序：lv(pIndex) - [mask/buffer] -> cas(pIndex)
            // 该优化背后的假设是队列几乎总是空的或接近空的 - 如果队列几乎总是满的，那么这里性能就很差

            // a successful CAS ties the ordering, lv(pIndex) - [mask/buffer] -> cas(pIndex)

            // assumption behind this optimization is that queue is almost always empty or near empty
            if (producerLimit <= pIndex)
            {
                int result = offerSlowPath(mask, pIndex, producerLimit);
                switch (result)
                {
                    case CONTINUE_TO_P_INDEX_CAS:
                        // 竞争更新producerLimit成功，尚有可用空间，当前索引可以进行填充
                        // 这个break有点迷惑性，跳出的是switch，而不是跳出循环
                        break;
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        // 队列已满，返回false
                        return false;
                    case QUEUE_RESIZE:
                        // 获取扩容权，进行扩容
                        resize(mask, buffer, pIndex, e, null);
                        return true;
                }
            }

            // pIndex未超过限制（缓存的或最新的限制），尝试竞争索引(+2是因为索引进行了左移)
            if (casProducerIndex(pIndex, pIndex + 2))
            {
                break;
            }
        }
        // 此时所以已经对其它线程可见，因此消费者必须处理索引可见，但元素尚未填充或尚不可见的情况
        // 使用Ordered模式尽快的对消费者可见，因为消费者依赖于element的可见性，以减少对producerIndex的依赖 - CAS已经保证了正确的构造。

        // INDEX visible before ELEMENT
        final long offset = modifiedCalcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e); // release element e
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null)
        {
            if (index != lvProducerIndex())
            {
                // 如果resize失败或supplier抛出异常，消费者将死锁。
                // 目前来看，所有的队列都有死锁风险，但是这里多了一个resize风险

                // index != producerIndex，表示有生产者正在填充或正在扩容，即将有新元素被填充，因此自旋等待，直到元素可见，
                // 以满足Queue对poll的语义要求：当且仅当队列为空时返回null。

                // poll() == null iff queue is empty, null element is not strong enough indicator, so we must
                // check the producer index. If the queue is indeed not empty we spin until element is
                // visible.
                do
                {
                    e = lvRefElement(buffer, offset);
                }
                while (e == null);
            }
            else
            {
                // 队列为空，返回null
                return null;
            }
        }

        // 到这里表示元素已对消费者可见（不为null），可能为普通元素或跳点
        // 元素为跳点，表示需要跳转到下一个数组
        if (e == JUMP)
        {
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }

        // 因为生产者保证了安全发布，因此这里可以直接消费，不必等待生产者索引可见
        soRefElement(buffer, offset, null); // release element null
        // 这里使用Ordered模式保证原子存储，以及尽快对生产者可见，以前确保元素清理在这之前完成（避免并发更新同一槽位）
        // 注意位移，因此这里实际是 真实索引+1
        soConsumerIndex(index + 2); // release cIndex
        return (E) e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E peek()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null && index != lvProducerIndex())
        {
            // 元素为null，但队列不为空，表示有生产者正在填充或正在扩容，即将有新元素被填充，因此自旋等待，直到元素可见，
            // 以满足Queue对peek的语义要求：当且仅当队列为空时返回null。

            // peek() == null iff queue is empty, null element is not strong enough indicator, so we must
            // check the producer index. If the queue is indeed not empty we spin until element is visible.
            do
            {
                e = lvRefElement(buffer, offset);
            }
            while (e == null);
        }
        // 到这里，元素可能为null，或JUMP标记，或普通元素
        if (e == JUMP)
        {
            // 元素为跳点标记，则更新消费的数组，然后从新数组中peek元素
            return newBufferPeek(nextBuffer(buffer, mask), index);
        }
        // 普通元素或null
        return (E) e;
    }

    /**
     * 我们不会在此方法中内联resize，因为我们不会在fill时调整大小。
     * 实际上fill时也调用resize了，注释可能有问题。。。
     *
     * We do not inline resize into this method because we do not resize on fill.
     */
    private int offerSlowPath(long mask, long pIndex, long producerLimit)
    {
        // 读取最新的消费者索引，判断队列是否已满，以及已满的情况下是否可以扩容
        final long cIndex = lvConsumerIndex();
        long bufferCapacity = getCurrentBufferCapacity(mask);

        if (cIndex + bufferCapacity > pIndex)
        {
            // 走到这，表示当前数组尚有可用空间，不必扩容
            // 注意：bufferCapacity一定小于等于队列的最大容量限制
            if (!casProducerLimit(producerLimit, cIndex + bufferCapacity))
            {
                // CAS失败，有可能是其它生产者进行了扩容（切换了数组），因此必须重试（读取最新的数组重试）
                // retry from top
                return RETRY;
            }
            else
            {
                // CAS成功，则pIndex一定还在当前数组，则可以CAS竞争索引
                // continue to pIndex CAS
                return CONTINUE_TO_P_INDEX_CAS;
            }
        }
        // 到这里表示当前数组已满，需要判断是否可以扩容
        // full and cannot grow
        else if (availableInQueue(pIndex, cIndex) <= 0)
        {
            // 没有可用空间，队列已满，且不可以扩容
            // offer should return false;
            return QUEUE_FULL;
        }
        // 在这里表示可以继续扩容，需要CAS竞争 -> 将最低位设置为1
        // grab index for resize -> set lower bit
        else if (casProducerIndex(pIndex, pIndex + 1))
        {
            // 赢得resize(扩容)权，接下来进行扩容
            // trigger a resize
            return QUEUE_RESIZE;
        }
        else
        {
            // 竞争扩容权失败，从头开始
            // failed resize attempt, retry from top
            return RETRY;
        }
    }

    /**
     * 计算队列中还有多少可用空间
     *
     * @return available elements in queue * 2
     * 由于pIndex和cIndex都是真实索引的二倍，因此返回结果也是可用空间的二倍，方便计算。
     */
    protected abstract long availableInQueue(long pIndex, long cIndex);

    /**
     * 其实是加载下一个数组，并更新为当前消费中的数组
     */
    @SuppressWarnings("unchecked")
    private E[] nextBuffer(final E[] buffer, final long mask)
    {
        final long offset = nextArrayOffset(mask);
        final E[] nextBuffer = (E[]) lvRefElement(buffer, offset);
        consumerBuffer = nextBuffer;
        // 再次注意mask的计算方式
        consumerMask = (length(nextBuffer) - 2) << 1;
        // 标记为已消费，迭代器使用到了该标记
        soRefElement(buffer, offset, BUFFER_CONSUMED);
        return nextBuffer;
    }

    private static long nextArrayOffset(long mask)
    {
        return modifiedCalcCircularRefElementOffset(mask + 2, Long.MAX_VALUE);
    }

    /**
     * 消费到跳点，需要从下一个数组poll元素
     * (此时已经调用{@link #nextBuffer(Object[], long)})
     */
    private E newBufferPoll(E[] nextBuffer, long index)
    {
        final long offset = modifiedCalcCircularRefElementOffset(index, consumerMask);
        final E n = lvRefElement(nextBuffer, offset);
        if (n == null)
        {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        // 由于生产者依赖的是消费者的索引，因此这里可以使用Plain模式清理
        soRefElement(nextBuffer, offset, null);
        // index + 2，实际上是真实索引+1
        soConsumerIndex(index + 2);
        return n;
    }

    /**
     * 消费到跳点，需要从下一个数组peek元素
     * (此时已经调用{@link #nextBuffer(Object[], long)})
     */
    private E newBufferPeek(E[] nextBuffer, long index)
    {
        final long offset = modifiedCalcCircularRefElementOffset(index, consumerMask);
        final E n = lvRefElement(nextBuffer, offset);
        if (null == n)
        {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        return n;
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex() / 2;
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex() / 2;
    }

    @Override
    public abstract int capacity();

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPoll()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == null)
        {
            // 此时可能队列为空，可能有生产者正在填充但元素尚不可见，由于是relaxedPoll，因此可以直接返回
            return null;
        }
        if (e == JUMP)
        {
            // 元素是跳点，更新当前消费数组为下一个数组，并从新数组中poll元素
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, index);
        }
        // 元素为普通元素，生产者进行了安全发布，因此可直接消费
        // 这里其实可以使用Plain模式清理元素，因为生产者依赖于消费者的索引，而不是element的可见性
        soRefElement(buffer, offset, null);
        soConsumerIndex(index + 2);
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    @Override
    public E relaxedPeek()
    {
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;

        final long offset = modifiedCalcCircularRefElementOffset(index, mask);
        Object e = lvRefElement(buffer, offset);
        if (e == JUMP)
        {
            // e为跳点，表示需要更新当前消费数组，并从新数组中peek元素
            return newBufferPeek(nextBuffer(buffer, mask), index);
        }
        // null或普通元素，可直接返回（生产者保证了安全发布）
        return (E) e;
    }

    @Override
    public int fill(Supplier<E> s)
    {
        long result = 0;// result is a long because we want to have a safepoint check at regular intervals
        final int capacity = capacity();
        do
        {
            final int filled = fill(s, PortableJvmInfo.RECOMENDED_OFFER_BATCH);
            if (filled == 0)
            {
                return (int) result;
            }
            result += filled;
        }
        while (result <= capacity);
        return (int) result;
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

        long mask;
        E[] buffer;
        long pIndex;
        int claimedSlots;
        while (true)
        {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            // lower bit is indicative of resize, if we see it we spin until it's cleared
            if ((pIndex & 1) == 1)
            {
                // 有生产者正在进行扩容，此时需要等待其扩容完成
                continue;
            }
            // 走到这，证明pIndex是个偶数 -> 真实索引为 pIndex >> 1
            // mask和buffer可能因为某个生产者扩容导致改变 -> 仅用于CAS成功后的数组访问

            // pIndex is even (lower bit is 0) -> actual index is (pIndex >> 1)

            // NOTE: mask/buffer may get changed by resizing -> only use for array access after successful CAS.
            // Only by virtue offloading them between the lvProducerIndex and a successful casProducerIndex are they
            // safe to use.
            mask = this.producerMask;
            buffer = this.producerBuffer;
            // a successful CAS ties the ordering, lv(pIndex) -> [mask/buffer] -> cas(pIndex)

            // 我们想要限制可用槽位，但将会设置为'producerLimit'可见的一切？？？
            // 换句话说：用户的limit值，会被修正为当前producerLimit下可见的limit值，以进行批量声明这些槽位
            // we want 'limit' slots, but will settle for whatever is visible to 'producerLimit'
            long batchIndex = Math.min(producerLimit, pIndex + 2l * limit); //  -> producerLimit >= batchIndex

            if (pIndex >= producerLimit)
            {
                int result = offerSlowPath(mask, pIndex, producerLimit);
                switch (result)
                {
                    case CONTINUE_TO_P_INDEX_CAS:
                        // offer slow path verifies only one slot ahead, we cannot rely on indication here
                        // offerSlowPath返回可以CAS竞争索引，仅仅表示可以竞争当前槽位（单个槽位），而我们这里需要批量竞争索引，因此不能依赖这里的返回值
                        // 必须重试，直到pIndex < producerLimit
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return 0;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, null, s);
                        return 1;
                }
            }

            // 注意，走到这里的时候：1. 只有pIndex < producerLimit才会走到这里。2. batchIndex <= producerLimit
            // 尝试批量申请这部分槽位，如果CAS成功，这这部分数据都是可填充的，
            // claim limit slots at once
            if (casProducerIndex(pIndex, batchIndex))
            {
                claimedSlots = (int) ((batchIndex - pIndex) / 2);
                break;
            }
        }

        // 批量声明数据成功，这部分槽位可以安全的进行填充
        for (int i = 0; i < claimedSlots; i++)
        {
            final long offset = modifiedCalcCircularRefElementOffset(pIndex + 2l * i, mask);
            // 这里使用Ordered模式保证安全发布
            // 谨记Supplier对get方法的约束，不可以抛出异常，不可以返回null，否则队列将被破坏
            soRefElement(buffer, offset, s.get());
        }
        return claimedSlots;
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }
    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    /**
     * Get an iterator for this queue. This method is thread safe.
     * <p>
     * The iterator provides a best-effort snapshot of the elements in the queue.
     * The returned iterator is not guaranteed to return elements in queue order,
     * and races with the consumer thread may cause gaps in the sequence of returned elements.
     * Like {link #relaxedPoll}, the iterator may not immediately return newly inserted elements.
     *
     * @return The iterator.
     */
    @Override
    public Iterator<E> iterator() {
        return new WeakIterator(consumerBuffer, lvConsumerIndex(), lvProducerIndex());
    }

    /**
     * 不译注该类，使用有限，不想费脑细胞。。。
     */
    private static class WeakIterator<E> implements Iterator<E>
    {
        private final long pIndex;
        private long nextIndex;
        private E nextElement;
        private E[] currentBuffer;
        private int mask;

        WeakIterator(E[] currentBuffer, long cIndex, long pIndex)
        {
            this.pIndex = pIndex >> 1;
            this.nextIndex = cIndex >> 1;
            setBuffer(currentBuffer);
            nextElement = getNext();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove");
        }

        @Override
        public boolean hasNext()
        {
            return nextElement != null;
        }

        @Override
        public E next()
        {
            final E e = nextElement;
            if (e == null)
            {
                throw new NoSuchElementException();
            }
            nextElement = getNext();
            return e;
        }

        private void setBuffer(E[] buffer)
        {
            this.currentBuffer = buffer;
            this.mask = length(buffer) - 2;
        }

        private E getNext()
        {
            while (nextIndex < pIndex)
            {
                long index = nextIndex++;
                E e = lvRefElement(currentBuffer, calcCircularRefElementOffset(index, mask));
                // skip removed/not yet visible elements
                if (e == null)
                {
                    continue;
                }

                // not null && not JUMP -> found next element
                if (e != JUMP)
                {
                    return e;
                }

                // need to jump to the next buffer
                int nextBufferIndex = mask + 1;
                Object nextBuffer = lvRefElement(currentBuffer,
                                              calcRefElementOffset(nextBufferIndex));

                if (nextBuffer == BUFFER_CONSUMED || nextBuffer == null)
                {
                    // Consumer may have passed us, or the next buffer is not visible yet: drop out early
                    return null;
                }

                setBuffer((E[]) nextBuffer);
                // now with the new array retry the load, it can't be a JUMP, but we need to repeat same index
                e = lvRefElement(currentBuffer, calcCircularRefElementOffset(index, mask));
                // skip removed/not yet visible elements
                if (e == null)
                {
                    continue;
                }
                else
                {
                    return e;
                }

            }
            return null;
        }
    }

    private void resize(long oldMask, E[] oldBuffer, long pIndex, E e, Supplier<E> s)
    {
        assert (e != null && s == null) || (e == null || s != null);
        int newBufferLength = getNextBufferSize(oldBuffer);
        final E[] newBuffer;
        try
        {
            newBuffer = allocateRefArray(newBufferLength);
        }
        catch (OutOfMemoryError oom)
        {
            assert lvProducerIndex() == pIndex + 1;
            // 回滚了生产者索引，非常危险，消费者如果在等待该索引，将出现问题
            // 不过目前来看，所有的队列都有死锁风险，比如supplier抛出异常，或返回null
            soProducerIndex(pIndex);
            throw oom;
        }

        // 更新当前使用的数组
        producerBuffer = newBuffer;
        final int newMask = (newBufferLength - 2) << 1;
        producerMask = newMask;

        final long offsetInOld = modifiedCalcCircularRefElementOffset(pIndex, oldMask);
        final long offsetInNew = modifiedCalcCircularRefElementOffset(pIndex, newMask);

        // 1. 新元素添加到了新数组中，此时尚不可达 - 该步可使用Plain模式存储，在更新生产者索引前不可达
        // 2. 旧数组链接到新数组，此时新数组也尚不可达 - 该步可使用Plain模式存储，在更新生产者索引前不可达
        // 3. 更新producerLimit - 此时是没有竞争的，可以确保producerLimit落在当前数组上
        // 4. 更新生产者索引 - 此时生产者索引对其它生产者和消费者可见，当其它生产者读取到最新索引时，将会跳转到到下一个数组。
        // 5. 添加JUMP到旧数组，此时JUMP对消费者可见，当消费者读取到JUMP时，知道链接已完成，且下一个元素也完成了存储。

        soRefElement(newBuffer, offsetInNew, e == null ? s.get() : e);// element in new array
        soRefElement(oldBuffer, nextArrayOffset(oldMask), newBuffer);// buffer linked

        // ASSERT code
        final long cIndex = lvConsumerIndex();
        final long availableInQueue = availableInQueue(pIndex, cIndex);
        RangeUtil.checkPositive(availableInQueue, "availableInQueue");

        // 索引更新都需要使用Ordered模式，以确保原子存储，此外producerIndex
        // Invalidate racing CASs
        // We never set the limit beyond the bounds of a buffer
        soProducerLimit(pIndex + Math.min(newMask, availableInQueue));

        // make resize visible to the other producers
        soProducerIndex(pIndex + 2);

        // INDEX visible before ELEMENT, consistent with consumer expectation

        // make resize visible to consumer
        soRefElement(oldBuffer, offsetInOld, JUMP);
    }

    // 下面这两个方法是相关的，因为扩容策略的不同，导致数组中有效长度不同
    // 主要由于Growable策略引起的，其扩容策略与其它队列不同，且到达最大容量以后，因为不会再扩容，因此不再为JUMP预留空间。

    /**
     * 获取下一个数组(buffer)的大小，返回值需要包括到下一个数组的指针
     *
     * @return next buffer size(inclusive of next array pointer)
     */
    protected abstract int getNextBufferSize(E[] buffer);

    /**
     * 获取当前数组的容量（不包含JUMP和到下一个数组的指针），由于mask是带有位移的，为了方便运算，返回值也需要进行位移运算。
     *
     * @return current buffer capacity for elements (excluding next pointer and jump entry) * 2
     */
    protected abstract long getCurrentBufferCapacity(long mask);
}
