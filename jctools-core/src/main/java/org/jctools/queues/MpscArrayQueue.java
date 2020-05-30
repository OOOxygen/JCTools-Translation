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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpscArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E>
{
    /**
     * 缓存行填充，避免{@code producerIndex}和超类{@link ConcurrentCircularArrayQueue}的{@code buffer}产生伪共享。
     * Q: 还有8个字节去哪儿？
     * A: 还有一个是我们要保护的数据自身,{@code producerIndex}是long类型。
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
    // byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    MpscArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueProducerIndexField<E> extends MpscArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(MpscArrayQueueProducerIndexField.class, "producerIndex");

    /**
     * 生产者索引(生产者的进度)。
     * <p>
     * 这是一个预更新值，生产者们先竞争该索引(+1或+n)，然后再填充数据到该索引对应的槽位;
     * 因此存在某个时刻部分索引对应的槽位并无数据，但在一段时间之后这些槽位都将被填充。
     * <p>
     * 注意：它表示的是下一个要填充元素索引，而不是已填充的索引。
     * <p>
     * 这也是缓存行填充避免与其它数据产生伪共享的字段。
     * <p>
     * Q: 为什么要声明为volatile？
     * A: https://github.com/google/j2objc/issues/803
     * 想不到吧...
     */
    private volatile long producerIndex;

    MpscArrayQueueProducerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileProducerIndex
     * 由于是多生产者模型,该值多线程更新,多线程读取,因此需要使用volatile模式读取最新值.
     * (J9可以选择Acquire(虽然底层可能也是volatile))
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * 由于是多生产者模型，producerIndex的更新必须保证原子性，只有更新成功的那个生产者才能填充这段区间对应的槽。
     * 即如果CAS成功，[expect, newValue-1]这段索引都可以使用
     */
    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpscArrayQueueMidPad<E> extends MpscArrayQueueProducerIndexField<E>
{
    /**
     * 缓存行填充,用于避免 producerIndex和producerLimit和其它数据产生伪共享
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

    MpscArrayQueueMidPad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueProducerLimitField<E> extends MpscArrayQueueMidPad<E>
{
    private final static long P_LIMIT_OFFSET = fieldOffset(MpscArrayQueueProducerLimitField.class, "producerLimit");

    /**
     * 在重新读取消费者索引之前，第一个不可用的生产者索引。
     * <p>
     * Q: 这个值有什么用，直接读取consumerIndex计算不行吗?
     * A: {@code consumerIndex}是一个变化较为频繁的值，因此它所在的缓存行极易失效，从而影响读性能。
     * 我们拷贝一个副本（并在副本无效的时候更新），这样可以减少生产者与消费者之间产生的伪共享，从而提高读效率.
     * <p>
     * Q: 该值为什么进行缓存行填充，为什么与producerIndex分离？
     * A: 因为是多生产模式，因此producerIndex上将产生高度竞争，因此其所在的缓存行极易失效，
     * 将该值与producerIndex分开，我们期望该值大部分时间位于用于共享（且很少失效）的缓存行中。
     * PS: 该值的更新频率远低于producerIndex。
     */
    // First unavailable index the producer may claim up to before rereading the consumer index
    private volatile long producerLimit;

    MpscArrayQueueProducerLimitField(int capacity)
    {
        super(capacity);
        this.producerLimit = capacity;
    }

    /**
     * loadVolatileProducerLimit
     * 由于是多生产者模型,该值多线程更新,多线程读取,因此需要使用volatile模式读取最新值.
     * (J9可以选择Acquire(虽然底层可能也是volatile))
     */
    final long lvProducerLimit()
    {
        return producerLimit;
    }

    /**
     * storeOrderedProducerLimit
     * 这里并没有使用volatile模式写，因为不需要立即对其它线程可见，每个线程都可以自己计算。
     * 这里也没有使用CAS模式更新，因为在上面的竞争是良性的，覆盖并不会导致错误。
     */
    final void soProducerLimit(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_LIMIT_OFFSET, newValue);
    }
}

abstract class MpscArrayQueueL2Pad<E> extends MpscArrayQueueProducerLimitField<E>
{
    /**
     * 缓存行填充，避免{@code producerIndex}和{@code consuemrIndex}上产生伪共享
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
    // byte b170,b171,b172,b173,b174,b175,b176,b177;//128b

    MpscArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpscArrayQueueConsumerIndexField<E> extends MpscArrayQueueL2Pad<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(MpscArrayQueueConsumerIndexField.class, "consumerIndex");

    /**
     * 消费者索引(当前消费进度).
     * 这是一个滞后值，消费者先消费可用槽位数据，再更新消费进度;
     */
    private volatile long consumerIndex;

    MpscArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileConsumerIndex
     * 当不确定是消费者线程时，使用该方法读取
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * loadPlainConsumerIndex
     * 消费者线程使用该方法读取即可，因为consumerIndex始终由消费者线程更新，消费者线程始终可以取到最新值。
     */
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    /**
     * storeOrderedConsumerIndex
     * 消费者线程使用该方法更新consumerIndex，需要保证存储的原子性，以及当其它线程看见该值时能确定元素已消费。
     * 这里使用Ordered模式写可满足需求(比起volatile不保证立即对其它线程的可见性)。
     */
    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class MpscArrayQueueL3Pad<E> extends MpscArrayQueueConsumerIndexField<E>
{
    /**
     * 缓存行填充，保护consumerIndex
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

    MpscArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}

/**
 * 基于{@link org.jctools.queues.ConcurrentCircularArrayQueue}的多生产者但消费者队列。
 * 这意味着任何线程都可以调用offer方法，但是只有一个线程可以调用poll/peek来保持正确性。
 * 此实现遵循在包级别记录的的用于避免伪共享的模式（缓存行填充）。
 * 此实现使用Fast Flow模式从队列中poll（稍作更改即可正确发布索引），并在生产者端对Leslie Lamport并发队列算法（源于Martin Thompson）进行了扩展。
 * 注意：Fast Flow模型下，当消费者发现元素存在时，就会进行消费，在多生产者模式下，这不会导致奇怪的状态，因为生产者会先竞争更新索引，当元素可见时，对应的索引一定已可见。
 *
 * A Multi-Producer-Single-Consumer queue based on a {@link org.jctools.queues.ConcurrentCircularArrayQueue}. This
 * implies that any thread may call the offer method, but only a single thread may call poll/peek for correctness to
 * maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * This implementation is using the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * method for polling from the queue (with minor change to correctly publish the index) and an extension of
 * the Leslie Lamport concurrent queue algorithm (originated by Martin Thompson) on the producer side.
 */
public class MpscArrayQueue<E> extends MpscArrayQueueL3Pad<E>
{

    public MpscArrayQueue(final int capacity)
    {
        super(capacity);
    }

    /**
     * 当{@link #size()} 小于给定threshold时才插入元素
     *
     * {@link #offer}} if {@link #size()} is less than threshold.
     *
     * @param e         the object to offer onto the queue, not null
     * @param threshold the maximum allowable size
     * @return true if the offer is successful, false if queue size exceeds threshold
     * @since 1.0.1
     */
    public boolean offerIfBelowThreshold(final E e, int threshold)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final long mask = this.mask;
        final long capacity = mask + 1;

        long producerLimit = lvProducerLimit();
        long pIndex;
        do
        {
            pIndex = lvProducerIndex();
            // 注意：这是根据缓存值算出来的可用空间，因为它是根据旧的consumerIndex计算出的，因此小于等于真实的可用空间
            long available = producerLimit - pIndex;
            long size = capacity - available;
            if (size >= threshold)
            {
                // 根据缓存值计算出的size大于等于阈值，可能是真的空间不足，也可能是缓存过期，需要读取最新的consumerIndex再次检查
                final long cIndex = lvConsumerIndex();
                size = pIndex - cIndex;
                if (size >= threshold)
                {
                    // 根据最新的consumerIndex计算出的size仍然大于阈值，那么不执行offer - 其实此处也可以考虑更新producerLimit（可能也变大了）
                    return false; // the size exceeds threshold
                }
                else
                {
                    // 根据最新的consumerIndex计算出的size小于阈值，需要尝试执行offer

                    // 更新producerLimit为下一个我们必须重新检查消费者索引的值
                    // update producer limit to the next index that we must recheck the consumer index
                    producerLimit = cIndex + capacity;

                    // 因为是多生产者模式，因此更新缓存会产生竞争。
                    // Q: 为什么竞争是良性的？
                    // A: 因为producerLimit永远不会超过下一次的计算值，而producerLimit小于实际值并不会带来错误。
                    // this is racy, but the race is benign
                    soProducerLimit(producerLimit);
                }
            }
        }
        while (!casProducerIndex(pIndex, pIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */
        // CAS 竞争成功，可以进行填充
        // 提示：新的生产者索引先于数组中的元素对其它线程可见。如果依赖于索引的可见性执行poll，我们需要处理元素尚不可见的情况（等待这里完成填充）。

        // Won CAS, move on to storing
        final long offset = calcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e);
        return true; // AWESOME :)
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 使用CAS进行无锁填充。如类名所示，允许同时访问多个线程。
     *
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free offer using a single CAS. As class name suggests access is permitted to many threads
     * concurrently.
     *
     * @see java.util.Queue#offer
     * @see org.jctools.queues.MessagePassingQueue#offer
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }

        // producerLimit基于consumerIndex计算的一个缓存值，用户减少对consumerIndex的读取（减少缓存行miss），在循环中可能更新
        // use a cached view on consumer index (potentially updated in loop)
        final long mask = this.mask;
        long producerLimit = lvProducerLimit();
        long pIndex;
        do
        {
            pIndex = lvProducerIndex();
            if (pIndex >= producerLimit)
            {
                // 生产者索引大于等于缓存的上限，表示根据缓存值认为队列已满。
                // 此时，分两种情况：1. 队列真的满了。 2.缓存过期了。
                // 因此需要读取最新的消费者索引，计算新的上限，判断队列是否是真的满了（以满足Queue对offer的语义要求）
                final long cIndex = lvConsumerIndex();
                producerLimit = cIndex + mask + 1;

                if (pIndex >= producerLimit)
                {
                    // 最新的消费者索引显式队列确实已满
                    // 只有当producerLimit大于producerIndex时更新才有意义，因此不更新producerLimit。
                    return false; // FULL :(
                }
                else
                {
                    // 更新producerLimit为下一个我们必须重新检查消费者索引的值
                    // 因为是多生产者模式，因此更新缓存会产生竞争。
                    // Q: 为什么竞争是良性的？
                    // A: 因为producerLimit永远不会超过下一次的计算值，而producerLimit小于实际值并不会带来错误。

                    // update producer limit to the next index that we must recheck the consumer index
                    // this is racy, but the race is benign
                    soProducerLimit(producerLimit);
                }
            }
        }
        while (!casProducerIndex(pIndex, pIndex + 1));
        /*
         * NOTE: the new producer index value is made visible BEFORE the element in the array. If we relied on
         * the index visibility to poll() we would need to handle the case where the element is not visible.
         */
        // CAS 竞争成功，可以进行填充
        // 提示：新的生产者索引值先于数组中的元素对其它线程可见。如果依赖于索引的可见性执行poll，我们将需要处理元素可能不可见的情况。

        // 前面的CAS已经保证了对象的正确构造（安全发布），这里使用Ordered模式是保证尽快的可见性（volatile是立即的可见性）。
        // Won CAS, move on to storing
        final long offset = calcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e);
        return true; // AWESOME :)
    }

    /**
     * {@link #offer(Object)}方法的无等待的替代方法，它会在CAS失败时失败，而不会重试。
     *
     * A wait free alternative to offer which fails on CAS failure.
     *
     * @param e new element, not null
     * @return 1 if next element cannot be filled, -1 if CAS failed, 0 if successful
     */
    public final int failFastOffer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long pIndex = lvProducerIndex();
        long producerLimit = lvProducerLimit();
        if (pIndex >= producerLimit)
        {
            // 根据缓存值，认为队列已满。此时需要读取最新的消费者索引，判断是真的已满，还是缓存失效
            final long cIndex = lvConsumerIndex();
            producerLimit = cIndex + capacity;
            if (pIndex >= producerLimit)
            {
                // 最新的消费者索引显式队列确实已满 - 不再解释为何不更新producerIndex，可查看offer实现
                return 1; // FULL :(
            }
            else
            {
                // 更新producerLimit为下一个我们必须重新检查消费者索引的值 - 不再解释竞争是良性的原因，可查看offer实现
                // update producer limit to the next index that we must recheck the consumer index
                soProducerLimit(producerLimit);
            }
        }

        // 尝试一次CAS更新，如果失败，则直接返回，如果成功，则插入元素
        // look Ma, no loop!
        if (!casProducerIndex(pIndex, pIndex + 1))
        {
            return -1; // CAS FAIL :(
        }

        // CAS 竞争成功，可以进行填充
        // 提示：新的生产者索引值先于数组中的元素对其它线程可见。如果依赖于索引的可见性执行poll，我们将需要处理元素可能不可见的情况。
        // 0 表示成功

        // 使用Ordered模式实现安全发布，其它线程读取到该对于引用时，可确保是构造完成的对象
        // Won CAS, move on to storing
        final long offset = calcCircularRefElementOffset(pIndex, mask);
        soRefElement(buffer, offset, e);
        return 0; // AWESOME :)
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现提示：<br>
     * 使用ordered loads/stores进行无锁poll。正如类名建议的那样，仅限于单个线程访问。
     *
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free poll using ordered loads/stores. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll
     * @see org.jctools.queues.MessagePassingQueue#poll
     */
    @Override
    public E poll()
    {
        final long cIndex = lpConsumerIndex();
        final long offset = calcCircularRefElementOffset(cIndex, mask);

        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        // 注意：生产者先更新索引，再填充元素，因此这里必须处理时序问题
        // 如果元素不为null，那么可以安全的消费，因为生产者索引一定可见，但是如果元素为null，那么则必须等待其不为null。
        // Q: 校验element而不是生产者索引，有什么好处？
        // A: 可以减少对生产者索引的读！如果元素可见，那么不必读取生产者索引，可以减少缓存行miss问题。

        // If we can't see the next available element we can't poll
        E e = lvRefElement(buffer, offset);
        if (null == e)
        {
            // null == e 有以下可能：
            // 1. 队列为空
            // 2. 生产者已经CAS更新了生产者索引，但是尚未填充元素，或填充的元素尚不可见 - 此时需要等待生产者完成填充，因为队列的状态表示当前并不为空！

            // 提示：如果生产者在CAS更新生产者索引之后填充元素之前被中断，在这种情况下，队列并不是真正的为空。其它生产者会在该元素之后继续填充队列。

            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex())
            {
                // 队列不为空，需要自旋等待直到元素可见 - 这也是比relaxedPool开销大的原因
                do
                {
                    e = lvRefElement(buffer, offset);
                }
                while (e == null);
            }
            else
            {
                // 消费者索引和生产者索引相同，证明队列确实为空
                return null;
            }
        }

        // 先消费元素，再更新消费者进度（因为生产者会先校验consumerIndex，因此可确保生产者不会覆盖数据）。
        // 这里可以使用Plain模式赋值为null，因为生产者一定会在索引可见之后才填充元素，consumerIndex的发布可以保证这里也正确发布。
        spRefElement(buffer, offset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Lock free peek using ordered loads. As class name suggests access is limited to a single thread.
     *
     * @see java.util.Queue#poll
     * @see org.jctools.queues.MessagePassingQueue#poll
     */
    @Override
    public E peek()
    {
        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        // Copy field to avoid re-reading after volatile load
        final E[] buffer = this.buffer;

        final long cIndex = lpConsumerIndex();
        final long offset = calcCircularRefElementOffset(cIndex, mask);

        // 注意：生产者先更新索引，再填充元素，因此这里必须处理时序问题，只有当元素可见时才能消费。
        // 如果元素不为null，那么可以安全的消费，因为生产者索引一定可见，但是如果元素为null，那么则必须等待。
        // Q: 校验element而不是生产者索引，有什么好处？
        // A: 可以减少对生产者索引的读！如果元素可见，那么不必读取生产者索引，可以减少缓存行miss问题。

        E e = lvRefElement(buffer, offset);
        if (null == e)
        {

            // null == e 有以下可能：
            // 1. 队列为空
            // 2. 生产者已经CAS更新了生产者索引，但是尚未填充元素，或填充的元素尚不可见 - 此时需要等待生产者完成填充，因为队列的状态表示当前并不为空！

            /*
             * NOTE: Queue may not actually be empty in the case of a producer (P1) being interrupted after
             * winning the CAS on offer but before storing the element in the queue. Other producers may go on
             * to fill up the queue after this element.
             */
            if (cIndex != lvProducerIndex())
            {
                // 队列不为空，需要自旋等待直到元素可见 - 这也是比relaxedPeek开销大的原因
                do
                {
                    e = lvRefElement(buffer, offset);
                }
                while (e == null);
            }
            else
            {
                // 消费者索引和生产者索引相同，证明队列确实为空
                return null;
            }
        }
        return e;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        // 为何没调用{@link #failFastOffer(Object)}？？？
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        final E[] buffer = this.buffer;
        final long cIndex = lpConsumerIndex();
        final long offset = calcCircularRefElementOffset(cIndex, mask);

        // If we can't see the next available element we can't poll
        E e = lvRefElement(buffer, offset);
        if (null == e)
        {
            // null == e 表示队列为空，或有生产者正在填充，或填充的数据尚不可见，在relaxedPoll语义下可以直接返回null，因此可以提高性能
            return null;
        }

        // 这里可以使用Plain模式赋值为null，因为生产者一定会在索引可见之后才填充元素，consumerIndex的发布可以保证这里也正确发布。
        spRefElement(buffer, offset, null);
        soConsumerIndex(cIndex + 1);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long cIndex = lpConsumerIndex();
        return lvRefElement(buffer, calcCircularRefElementOffset(cIndex, mask));
    }

    @Override
    public int drain(final Consumer<E> c, final int limit)
    {
        if (null == c)
            throw new IllegalArgumentException("c is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative: " + limit);
        if (limit == 0)
            return 0;

        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long cIndex = lpConsumerIndex();

        // 居然是一个元素一个元素消费的，还以为会批量消费
        for (int i = 0; i < limit; i++)
        {
            final long index = cIndex + i;
            final long offset = calcCircularRefElementOffset(index, mask);
            final E e = lvRefElement(buffer, offset);
            if (null == e)
            {
                // null == e 表示队列为空，或有生产者正在填充，或填充的数据尚不可见。
                // 在接口说明中，约定了drain的语义为relaxedPoll，因此不尽最大努力获取元素，当前可消费多少就消费多少，不阻塞
                return i;
            }
            // 这里可以使用Plain模式赋值为null，因为生产者一定会在索引可见之后才填充元素，consumerIndex的发布可以保证这里也正确发布。
            spRefElement(buffer, offset, null);
            soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
            // 消费元素 - 根据接口约定，该实现不应该抛出异常，虽然在当前队列实现是安全的，但是抛出异常可能在某些实现先破坏队列的状态。
            c.accept(e);
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

        final long mask = this.mask;
        final long capacity = mask + 1;
        long producerLimit = lvProducerLimit();
        long pIndex;
        // 真正可填充的元素数量上限
        int actualLimit = 0;
        do
        {
            pIndex = lvProducerIndex();
            long available = producerLimit - pIndex;
            if (available <= 0)
            {
                // 根据缓存值推断表示队列已满，此时可能队列是真的满了，也可能是缓存过期了。
                // 这里读取了最新的consumerIndex，判断队列是否已满。
                final long cIndex = lvConsumerIndex();
                producerLimit = cIndex + capacity;
                available = producerLimit - pIndex;
                if (available <= 0)
                {
                    // 最新的消费者索引表示队列是真的满了，无法插入元素。
                    // Q: 这里为什么返回0？
                    // A: 因为CAS成功就会退出循环，因此在循环内一定没有填充元素。
                    return 0; // FULL :(
                }
                else
                {
                    // 更新producerLimit为下一个我们必须重新检查消费者索引的值 - 不再解释竞争是良性的原因，可查看offer实现
                    // update producer limit to the next index that we must recheck the consumer index
                    soProducerLimit(producerLimit);
                }
            }
            actualLimit = Math.min((int) available, limit);
        }
        while (!casProducerIndex(pIndex, pIndex + actualLimit));
        // 这里CAS成功就停止了循环，已经声明了这段空间，接下来可以安静的进行填充。
        // right, now we claimed a few slots and can fill them with goodness
        final E[] buffer = this.buffer;
        for (int i = 0; i < actualLimit; i++)
        {
            // 注意：Supplier中对get方法的约束：不可抛出异常，不可返回null，否则将队列将损坏，消费者poll/peek将死锁。
            // Won CAS, move on to storing
            final long offset = calcCircularRefElementOffset(pIndex + i, mask);
            soRefElement(buffer, offset, s.get());
        }
        return actualLimit;
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int fill(Supplier<E> s)
    {
        return MessagePassingQueueUtil.fillBounded(this, s);
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy w, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, w, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }
}
