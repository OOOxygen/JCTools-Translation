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

abstract class SpmcArrayQueueL1Pad<E> extends ConcurrentCircularArrayQueue<E>
{
    /**
     * 缓存行填充，避免{@code producerIndex}和超类{@link ConcurrentCircularArrayQueue}的{@code buffer}产生伪共享。
     * <p>
     * 这里似乎可以少8个字节？因为超类有字段超过8字节，下面的{@code producerIndex}8字节也可以充当填充。
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

    SpmcArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class SpmcArrayQueueProducerIndexField<E> extends SpmcArrayQueueL1Pad<E>
{
    protected final static long P_INDEX_OFFSET = fieldOffset(SpmcArrayQueueProducerIndexField.class,"producerIndex");

    /**
     * 生产者索引。
     * <p>
     * producerIndex表示的是当前要填充的元素索引，小于该索引的元素已被填充。
     * eg：producerIndex为1024时表示1024号索引的元素尚未填充，而0-1023已填充。
     * <p>
     * 生产者先发布元素，再更新索引在，这样当消费者CAS竞争索引之后，可确保元素已存在。
     */
    private volatile long producerIndex;

    SpmcArrayQueueProducerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileProducerIndex
     * 当不确定是生产者的情况下，需要使用volatile语义读取。
     * （在J9的VarHandle可以选择Acquire模式，虽然底层很可能也是使用volatile实现的）
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * loadPlainProducerIndex
     * 由于单生产者，该值只有生产者线程会修改，对于生产者线程而言，它只需要使用普通模式读取即可。
     */
    final long lpProducerIndex()
    {
        return UNSAFE.getLong(this, P_INDEX_OFFSET);
    }

    /**
     * storeOrderedProducerIndex
     * 在发布索引时，需要保证原子存储，以及当其它线程读取到该值时，能确定元素已填充完成。
     */
    final void soProducerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

}

abstract class SpmcArrayQueueL2Pad<E> extends SpmcArrayQueueProducerIndexField<E>
{
    /**
     * 缓存行填充，避免{@code producerIndex}和{@code consumerIndex}之间产生伪共享。
     * 我觉得这是复制的代码块，之前是120字节。。。
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

    SpmcArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class SpmcArrayQueueConsumerIndexField<E> extends SpmcArrayQueueL2Pad<E>
{
    protected final static long C_INDEX_OFFSET = fieldOffset(SpmcArrayQueueConsumerIndexField.class, "consumerIndex");

    /**
     * 消费者索引。
     * <p>
     * consumerIndex表示的是将要消费的元素索引，小于该索引的元素已被消费（或正在消费）。
     * eg： consumerIndex为1024表示1024索引对应的元素尚未消费，而0-1023对应的元素已被消费（或有部分正在被消费）。
     * <p>
     * 由于是多消费者模型，消费者需要先竞争索引，才能消费。
     */
    private volatile long consumerIndex;

    SpmcArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileConsumerIndex
     * 由于是多消费者模型，因此不可以使用Plain模式读，只能使用volatile读 - 在J9可以选择Acquire模式读。
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * 由于是多消费者模式，因此consumerIndex是并发更新的，需要保证原子性。
     */
    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }
}

abstract class SpmcArrayQueueMidPad<E> extends SpmcArrayQueueConsumerIndexField<E>
{
    /**
     * 缓存行填充 - 用于避免{@code consumerIndex}和{@code producerIndexCache}之间产生伪共享。
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

    SpmcArrayQueueMidPad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class SpmcArrayQueueProducerIndexCacheField<E> extends SpmcArrayQueueMidPad<E>
{

    /**
     * 生产者索引缓存。
     * <p>
     * Q: 这个值有什么用，直接读取producerIndex计算不行吗?
     * A: {@code producerIndex}是一个变化较为频繁的值，因此它所在的缓存行极易失效，如果频繁读取{@code producerIndex}，势必产生大量的伪共享，从而影响读性能。
     * 我们拷贝一个副本（并在副本无效的时候更新），这样可以减少生产者与消费者之间产生的伪共享，从而提高读效率.
     * <p>
     * Q: 该值为什么进行缓存行填充，为什么与consumerIndex分离？
     * A: 因为是多消费者模式，因此consumerIndex上将产生高度竞争，因此其所在的缓存行极易失效，
     * 将该值与consumerIndex分开，我们期望该值大部分时间位于用于共享（且很少失效）的缓存行中。
     */
    // This is separated from the consumerIndex which will be highly contended in the hope that this value spends most
    // of it's time in a cache line that is Shared(and rarely invalidated)
    private volatile long producerIndexCache;

    SpmcArrayQueueProducerIndexCacheField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileProducerIndexCache
     * 因为是多消费者模式，不可以使用Plain模式读写（需要保证读写原子性）
     */
    protected final long lvProducerIndexCache()
    {
        return producerIndexCache;
    }

    /**
     * storeVolatileProducerIndexCache
     * 这里使用了volatile模式写，在JCTools中很少用到，原因如下：
     * 1. 因为是多消费者模式，不可以使用Plain模式读写（需要保证读写原子性） - 至少需要使用Ordered模式。
     * 2. 消费者的速度可能很快，volatile更新缓存对其它消费者立即可见，可以避免大量的读取生产者索引 - 这个是个人见解。
     */
    protected final void svProducerIndexCache(long newValue)
    {
        producerIndexCache = newValue;
    }
}

abstract class SpmcArrayQueueL3Pad<E> extends SpmcArrayQueueProducerIndexCacheField<E>
{
    /**
     * 缓存行填充，避免{@code producerIndexCache}产生伪共享。
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

    SpmcArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}

/**
 * 基于数组的单生产者多消费者队列，可对比{@link MpscArrayQueue}理解，颠倒了两者的关系，实现上的表现就是offer与poll的颠倒。
 */
public class SpmcArrayQueue<E> extends SpmcArrayQueueL3Pad<E>
{

    public SpmcArrayQueue(final int capacity)
    {
        super(capacity);
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        final E[] buffer = this.buffer;
        final long mask = this.mask;

        // 由于是单生产者，因此生产者索引使用可以存为临时变量的，这期间并不会修改
        final long currProducerIndex = lvProducerIndex();
        final long offset = calcCircularRefElementOffset(currProducerIndex, mask);

        // 实现提示：消费者先竞争更新消费者索引，后清除element，因此当element为null时，我们可以不必读取消费者索引，从而减少缓存行miss问题
        // 但是如果element不为null，在offer的语义下，需要读取消费者进度判断队列是否真的已满
        if (null != lvRefElement(buffer, offset))
        {
            long size = currProducerIndex - lvConsumerIndex();

            if (size > mask)
            {
                // 队列确实已满
                return false;
            }
            else
            {
                // 有消费者正在消费该元素，这里自旋等待，直到看见消费者成功删除元素 - 等待时间不确定，但是假设不会太长。
                // 这样是offer开销大于relaxedOffer的原因。
                // Bubble: This can happen because `poll` moves index before placing element.
                // spin wait for slot to clear, buggers wait freedom
                while (null != lvRefElement(buffer, offset))
                {
                    // BURN
                }
            }
        }

        // 到这里表示消费者索引和删除元素都已对生产者可见，此时可以正式填充元素。
        // 与poll/peek对应，消费者先确保了索引可见，然后再消费，因此不会出现消费者索引大于生产者索引的情况。

        // 这里使用ordered模式存储，确保正确的构造和安全发布
        soRefElement(buffer, offset, e);
        // 这里使用ordered模式存储，确保原子存储，以及size约束（先更新元素，再更新size）
        // 因为是单生产者，因此使用Ordered模式存储是有效的。它同时要求了正确的发布element并允许消费者获取尾部值。
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soProducerIndex(currProducerIndex + 1);
        return true;
    }

    @Override
    public E poll()
    {
        long currentConsumerIndex;
        long currProducerIndexCache = lvProducerIndexCache();
        do
        {
            currentConsumerIndex = lvConsumerIndex();
            if (currentConsumerIndex >= currProducerIndexCache)
            {
                // 消费者索引大于等于缓存的生产者索引，此时可能 队列为空 或者 缓存过期，因此需要读取最新的生产者索引判断队列是否是真的为空
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex)
                {
                    // 队列是真的为空
                    return null;
                }
                else
                {
                    // 缓存过期了，更新局部变量和缓存变量
                    currProducerIndexCache = currProducerIndex;
                    svProducerIndexCache(currProducerIndex);
                }
            }
            // 到这里，可能是根据缓存的生产者索引认为队列不为空，也可能是根据最新的生产者索引认为队列不为空，
            // 此时需要竞争更新消费者索引，更新成功的消费者可以消费该索引对应的元素
        }
        while (!casConsumerIndex(currentConsumerIndex, currentConsumerIndex + 1));

        // Q: 如何保证正确性的？
        // A: 生产者是根据元素是否为null判断消费者是否已消费该元素。
        // 即使有多个消费者都成功更新了consumerIndex，总有一个消费者处在队尾！！！
        // 因此生产者不会越过队尾，也不能跳过队尾去填充后面可能已经被其它消费者消费了的槽位。

        // 消费者位于最后可见的尾部，因此无法在队列中看到null值（其它可能已经被消费的元素），因此生产者也无法覆盖并覆盖以到达相同位置。

        // consumers are gated on latest visible tail, and so can't see a null value in the queue or overtake
        // and wrap to hit same location.
        return removeElement(buffer, currentConsumerIndex, mask);
    }

    private E removeElement(final E[] buffer, long index, final long mask)
    {
        // 这里使用Plain模式加载元素，因为生产者先填充元素，然后更新索引(Ordered Mode)，
        // 走到这里的时候，索引已经对消费者可见，因此元素必定可见。
        // 这里使用Ordered模式清除元素，可确保null尽快对生产者可见，生产者会等待变为null之后才填充元素。

        final long offset = calcCircularRefElementOffset(index, mask);
        // load plain, element happens before it's index becomes visible
        final E e = lpRefElement(buffer, offset);
        // store ordered, make sure nulling out is visible. Producer is waiting for this value.
        soRefElement(buffer, offset, null);
        return e;
    }

    @Override
    public E peek()
    {
        // 加载为本地变量，避免在volatile读之后重复加载
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long currProducerIndexCache = lvProducerIndexCache();
        long currentConsumerIndex;
        long nextConsumerIndex = lvConsumerIndex();
        E e;
        do
        {
            currentConsumerIndex = nextConsumerIndex;
            if (currentConsumerIndex >= currProducerIndexCache)
            {
                // 消费者索引大于等于缓存的生产者索引，此时可能 队列为空 或者 缓存过期，因此需要读取最新的生产者索引判断队列是否是真的为空
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex)
                {
                    // 队列是真的为空
                    return null;
                }
                else
                {
                    // 缓存过期了，更新局部变量和缓存变量
                    currProducerIndexCache = currProducerIndex;
                    svProducerIndexCache(currProducerIndex);
                }
            }

            // 解释下：由于加载lvConsumerIndex和lvRefElement这是一个组合操作，
            // 在多消费者情况下，无法保证lvRefElement加载的element是属于这个索引的，可能读取到下一环的元素，因此需要校验。
            // 在加载该consumerIndex对应元素之后，如果消费者索引没有发生改变，那么证明这期间没有消费者消费，那么加载的元素就是我们期望的。
            // 时序很重要，这三个加载指令都不能重排序，因此都需要使用volatile语义，否则将无法校验（类似StampedLock的用法）

            e = lvRefElement(buffer, calcCircularRefElementOffset(currentConsumerIndex, mask));
            // sandwich the element load between 2 consumer index loads
            nextConsumerIndex = lvConsumerIndex();

            // null == e 和 nextConsumerIndex != currentConsumerIndex 都表示该位置元素已经被其它消费者消费，需要进行重试
        }
        while (null == e || nextConsumerIndex != currentConsumerIndex);
        return e;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException("Null is not a valid element");
        }
        // 加载为本地变量，避免在volatile读之后重复加载
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long producerIndex = lpProducerIndex();
        final long offset = calcCircularRefElementOffset(producerIndex, mask);

        // 实现提示：消费者先竞争更新消费者索引，后清除element，因此当element为null时，我们可以不必读取消费者索引，从而减少缓存行miss问题
        if (null != lvRefElement(buffer, offset))
        {
            // 宽松的版的offer实现，此时不判断消费者是否正在消费该元素，而是直接失败，可以提高吞吐量，减少竞争和阻塞时间
            return false;
        }

        // 这里使用ordered模式存储，确保正确的构造和安全发布
        soRefElement(buffer, offset, e);
        // 这里使用ordered模式存储，确保原子存储，以及size约束（先更新元素，再更新size）
        // 因为是单生产者，因此使用Ordered模式存储是有效的。它同时要求了正确的发布element并允许消费者获取尾部值。
        // single producer, so store ordered is valid. It is also required to correctly publish the element
        // and for the consumers to pick up the tail value.
        soProducerIndex(producerIndex + 1);
        return true;
    }

    @Override
    public E relaxedPoll()
    {
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long currentConsumerIndex;
        long nextConsumerIndex = lvConsumerIndex();
        E e;
        do
        {
            // 解释下：由于加载lvConsumerIndex和lvRefElement这是一个组合操作，
            // 在多消费者情况下，无法保证lvRefElement加载的element是属于这个索引的，可能读取到下一环的元素，因此需要校验。
            // 在加载该consumerIndex对应元素之后，如果消费者索引没有发生改变，那么证明这期间没有消费者消费，那么加载的元素就是我们期望的。
            // 时序很重要，这三个加载指令都不能重排序，因此都需要使用volatile语义，否则将无法校验（类似StampedLock的用法）

            currentConsumerIndex = nextConsumerIndex;
            e = lvRefElement(buffer, calcCircularRefElementOffset(currentConsumerIndex, mask));
            // sandwich the element load between 2 consumer index loads
            nextConsumerIndex = lvConsumerIndex();
        }
        while (nextConsumerIndex != currentConsumerIndex);
        return e;
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

        // 加载为本地变量，避免在volatile读之后重复加载
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long currProducerIndexCache = lvProducerIndexCache();
        int adjustedLimit = 0;
        long currentConsumerIndex;
        do
        {
            currentConsumerIndex = lvConsumerIndex();
            // is there any space in the queue?
            if (currentConsumerIndex >= currProducerIndexCache)
            {
                // 消费者索引大于等于缓存的生产者索引，此时可能 队列为空 或者 缓存过期，因此需要读取最新的生产者索引判断队列是否是真的为空
                long currProducerIndex = lvProducerIndex();
                if (currentConsumerIndex >= currProducerIndex)
                {
                    // 队列真的为空
                    // Q: 这里为什么返回0？
                    // A: 因为CAS成功就会退出循环，因此在循环内一定没有消费元素。
                    return 0;
                }
                else
                {
                    // 缓存过期了，更新局部变量和缓存变量
                    currProducerIndexCache = currProducerIndex;
                    svProducerIndexCache(currProducerIndex);
                }
            }
            // 尝试批量声明要消费的元素，即CAS更新消费者索引，如果竞争成功，则可以安心的消费这部分元素
            // try and claim up to 'limit' elements in one go
            int remaining = (int) (currProducerIndexCache - currentConsumerIndex);
            adjustedLimit = Math.min(remaining, limit);
        }
        while (!casConsumerIndex(currentConsumerIndex, currentConsumerIndex + adjustedLimit));
        // CAS竞争成功，这些元素不会被其它消费者消费，生产者也不会覆盖我尚未消费的元素
        // 注意：这里CAS成功一次之后就退出循环了，而没有尽最大努力去消费，因为接口中drain的底层语义是relaxedPoll
        for (int i = 0; i < adjustedLimit; i++)
        {
            // 注意Consumer接口对accept方法的假设：不可抛出异常。
            // 一旦抛出异常，可能存在部分未消费的元素，从而导致生产者永久阻塞在未能消费的元素之前(一直等待消费将其变为null，但实际永远不会变为null了)，即死锁
            c.accept(removeElement(buffer, currentConsumerIndex + i, mask));
        }
        return adjustedLimit;
    }


    @Override
    public int fill(final Supplier<E> s, final int limit)
    {
        if (null == s)
            throw new IllegalArgumentException("supplier is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative:" + limit);
        if (limit == 0)
            return 0;

        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long producerIndex = this.lpProducerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long offset = calcCircularRefElementOffset(producerIndex, mask);
            if (null != lvRefElement(buffer, offset))
            {
                // 该索引元素不为null则返回，因为接口中fill底层语义是relaxedOffer，在队列可能满的时候就返回
                return i;
            }
            // 由于单生产者，因此可以利用局部变量进行循环，而不必再读
            producerIndex++;

            // 这里使用ordered模式存储，确保正确的构造和安全发布
            soRefElement(buffer, offset, s.get());
            // 这里使用ordered模式存储，确保原子存储和size约束（先更新元素，再更新size）
            soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
        }
        return limit;
    }

    @Override
    public int drain(final Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
    }

    @Override
    public int fill(final Supplier<E> s)
    {
        return fill(s, capacity());
    }

    @Override
    public void drain(final Consumer<E> c, final WaitStrategy w, final ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, w, exit);
    }

    @Override
    public void fill(final Supplier<E> s, final WaitStrategy w, final ExitCondition e)
    {
        MessagePassingQueueUtil.fill(this, s, w, e);
    }
}
