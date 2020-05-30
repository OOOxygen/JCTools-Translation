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

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.LinkedArrayQueueUtil.length;
import static org.jctools.queues.LinkedArrayQueueUtil.nextArrayOffset;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class BaseSpscLinkedArrayQueuePrePad<E> extends AbstractQueue<E> implements IndexedQueue
{
    /**
     * 缓存行填充，避免{@code consumerIndex}上产生伪共享
     * -
     * 主要目的是保护{@code consumerIndex}，因此冷数据{@code consumerMask}也充当了填充字段，其实还有对象头。
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
    //byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    //byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
    //  * drop 16b , the cold fields act as buffer *
}

abstract class BaseSpscLinkedArrayQueueConsumerColdFields<E> extends BaseSpscLinkedArrayQueuePrePad<E>
{
    /**
     * 消费者当前消费的数组的掩码
     * Q: 为什么为{@code buffer.length - 2}？
     * A: 因为额外分配了一个槽位用于存储到下一个数组的指针。
     */
    protected long consumerMask;
    /**
     * 消费者当前消费的数组
     */
    protected E[] consumerBuffer;
}

// $gen:ordered-fields
abstract class BaseSpscLinkedArrayQueueConsumerField<E> extends BaseSpscLinkedArrayQueueConsumerColdFields<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(BaseSpscLinkedArrayQueueConsumerField.class, "consumerIndex");

    /**
     * 消费者索引 - 目前的机制是先更新索引，再清除元素。
     */
    private volatile long consumerIndex;

    /**
     * loadVolatileConsumerIndex
     * 当不确定是消费者线程时，必须使用该方法读取
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * loadPlainConsumerIndex
     * 消费者线程使用该方法读取即可
     */
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    /**
     * storeOrderedConsumerIndex
     * 消费者更新索引时，需要保证原子存储，可能还依赖于其提供的内存屏障。
     */
    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }

}

abstract class BaseSpscLinkedArrayQueueL2Pad<E> extends BaseSpscLinkedArrayQueueConsumerField<E>
{
    /**
     * 缓存行填充，避免{@code consumerIndex}和{@code producerIndex}产生伪共享。
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
abstract class BaseSpscLinkedArrayQueueProducerFields<E> extends BaseSpscLinkedArrayQueueL2Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(BaseSpscLinkedArrayQueueProducerFields.class,"producerIndex");

    /**
     * 生产者索引 - 目前的机制是先填充元素，再更新索引。
     */
    private volatile long producerIndex;

    /**
     * loadVolatileProducerIndex
     * 非生产者使用该方法读取
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * storeOrderedProducerIndex
     * 生产者使用该方法修改索引，需要保证原子存储，可能还依赖于其提供的内存屏障。
     */
    final void soProducerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

    /**
     * loadPlainProducerIndex
     * 生产者可以直接使用该方法读取
     */
    final long lpProducerIndex()
    {
        return UNSAFE.getLong(this, P_INDEX_OFFSET);
    }

}

abstract class BaseSpscLinkedArrayQueueProducerColdFields<E> extends BaseSpscLinkedArrayQueueProducerFields<E>
{
    /**
     * 生产者在当前{@link #producerBuffer}上第一个不可使用的生产者索引。
     * 主意：该值一定对应于{@link #producerBuffer}，不会指向下一个数组。
     * <p>
     * Q: 为什么更新时总是伴随着{@code - 1}?
     * A: 因为需要预留一个槽位存储JUMP标记，因此实际可用空间要减1。
     */
    protected long producerBufferLimit;
    /**
     * 生产者当前使用的数组的掩码。
     * Q: 为什么为{@code buffer.length - 2}？
     * A: 因为额外分配了一个槽位用于存储到下一个数组的指针。
     */
    protected long producerMask; // fixed for chunked and unbounded

    /**
     * 生产者当前使用的数组
     */
    protected E[] producerBuffer;
}

/**
 * Q: 关于{@code LinkedArrayQueue}?
 * A: 队列由多个环形数组构成，在分配数组空间时，会多分配一个元素的空间，用于存储到下一个数组的指针（固定为数组的最后一个元素）。
 * 如果当前数组已满（谨记环形数组），则分配一个新的数组空间，并使用额外申请的那个槽位指向新的数组。
 * <p>
 * 使用环形数组由诸多好出:
 * 1. 可以反复利用分配的空间，只有在必要时才申请新的空间。
 * 2. 不必纠结索引转换问题，可以使用全局索引（pIndex,cIndex），而不必每个数组一个索引。
 * 3. 由于使用mask和index计算真实槽位，因此额外分配的槽位是不会被计算到的，更加简单。
 * <p>
 * PS: 这是很优秀的设计，应该学习。
 * <p>
 * 仍然是Fast Flow模型的队列，因此也会出现和{@link SpscArrayQueue}相似的索引重排序问题，
 * 不过在对{@link IndexedQueueSizeUtil}修改中，允许了这种情况，并对外进行了屏蔽。
 * <p>
 * 对于消费者：
 * 1. 遇见NULL表示队列为空。
 * 2. 遇见JUMP就跳跃到下一个数组。
 * 3. 遇见JUMP以外的非NULL值，则表示这是一个普通元素，可直接消费。
 * <p>
 * 对于生产者：
 * 1. 遇见NULL表示可以填充。
 * 2. 遇见非NULL表示队列已满，进行扩容或返回失败。
 */
abstract class BaseSpscLinkedArrayQueue<E> extends BaseSpscLinkedArrayQueueProducerColdFields<E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{

    /**
     * 跳点标记，表示当前索引对应的元素在下一个数组。
     * 对于生产者而言，表示resize已完成，大家需要到下一个数组竞争索引和填充元素。
     * 对于消费者而言，表示当前数组已消费完成，需要跳转到下一个数组进行消费。
     */
    private static final Object JUMP = new Object();

    @Override
    public final Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public final boolean isEmpty()
    {
        return IndexedQueueSizeUtil.isEmpty(this);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

    /**
     * storeOrderedNext
     * 将旧数组链接到新数组，需要提供时序保证。
     * （使用Ordered模式，可保证消费者在读取到下一个数组的指针时，上一个元素已可见）
     * PS：实际发现这里的Ordered模式是冗余的，因为在发布{@link #JUMP}之前，下一个数组是不可达的。
     */
    protected final void soNext(E[] curr, E[] next)
    {
        long offset = nextArrayOffset(curr);
        soRefElement(curr, offset, next);
    }

    /**
     * loadVolatileNextArrayAndUnlink
     * 加载下一个数组，并断开到下一个数组的连接。
     * （使用volatile模式，以确保它在接下来的读操作之前完成，以确保接下来的读操作是有效的）
     * PS: 这里的volatile语义似乎也是多余的。
     */
    @SuppressWarnings("unchecked")
    protected final E[] lvNextArrayAndUnlink(E[] curr)
    {
        final long offset = nextArrayOffset(curr);
        final E[] nextBuffer = (E[]) lvRefElement(curr, offset);
        // 断开连接，避免潜在的GC裙带关系(help gc)
        // prevent GC nepotism
        soRefElement(curr, offset, null);
        return nextBuffer;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public E relaxedPoll()
    {
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        return peek();
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
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
    public int drain(Consumer<E> c, int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
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

        for (int i = 0; i < limit; i++)
        {
            // Q: 这些变量为什么要放在循环中？
            // A: 因为在插入队列的过程中，可能创建新的数组。

            // local load of field to avoid repeated loads after volatile reads
            final E[] buffer = producerBuffer;
            final long index = lpProducerIndex();
            final long mask = producerMask;
            final long offset = calcCircularRefElementOffset(index, mask);
            // expected hot path
            if (index < producerBufferLimit)
            {
                // 这里写成for循环插入会更好点，可以减少上面的读 - 这里不会创建新的数组
                // 因为是单生产者，因此如果生产者索引如果小于当前限制，则可以确定可以安全的插入
                writeToQueue(buffer, s.get(), index, offset);
            }
            else
            {
                if (!offerColdPath(buffer, mask, index, offset, null, s))
                {
                    // 队列已满
                    return i;
                }
            }
        }
        return limit;
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e)
    {
        // Objects.requireNonNull(e);
        if (null == e)
        {
            throw new NullPointerException();
        }
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = producerBuffer;
        final long index = lpProducerIndex();
        final long mask = producerMask;
        final long offset = calcCircularRefElementOffset(index, mask);
        // expected hot path
        if (index < producerBufferLimit)
        {
            // 因为是单生产者，因此如果生产者索引如果小于当前限制，则可以确定可以安全的插入
            writeToQueue(buffer, e, index, offset);
            return true;
        }
        // index达到当前限制，需要进行观望或检查消费者索引，以确定下一步
        return offerColdPath(buffer, mask, index, offset, e, null);
    }

    /**
     * 通过冷路径插入元素 - 根据缓存认为当前不能插入元素，可能需要调整{@link #producerBufferLimit}或创建新的数组。
     * 冷路径：我们认为可能很少执行的代码块。既然只是个期望，那么也不能说真的很少走到（到达容量上限，而消费者消费较慢时就会频繁走到）。
     *
     * @param buffer 当前数组
     * @param mask   当前数组对应的掩码
     * @param pIndex 当前的生产者索引
     * @param offset 索引对应的地址偏移量
     * @param v      如果是{@link #offer(Object)}调用，则value不为null
     * @param s      如果是{@link #fill(Supplier, int)}调用，则supplier不为null
     * @return 插入成功则返回true，否则返回false（队列已满）
     */
    abstract boolean offerColdPath(
        E[] buffer,
        long mask,
        long pIndex,
        long offset,
        E v,
        Supplier<? extends E> s);

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @SuppressWarnings("unchecked")
    @Override
    public E poll()
    {
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = consumerBuffer;
        final long index = lpConsumerIndex();
        final long mask = consumerMask;
        final long offset = calcCircularRefElementOffset(index, mask);
        final Object e = lvRefElement(buffer, offset);
        boolean isNextBuffer = e == JUMP;
        if (null != e && !isNextBuffer)
        {
            // 下一个元素不为null，且不是跳点标记，即该元素是一个正常元素，直接返回。
            // 生产者的安全发布，保证了这里可以直接消费，而不必等待生产者索引可见

            // 有子类在扩容时依赖消费者索引，因此先更新了consumerIndex
            // 更新索引和清理元素在Fast Flow模型队列下是可以调整顺序的（其实都会有重排序问题）
            // 生产者是根据element是否为null，进行下一步的，因此先清理element，可能更好

            // Ordered模式确保原子存储
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soRefElement(buffer, offset, null);
            return (E) e;
        }
        else if (isNextBuffer)
        {
            // 当前元素为跳点标记，表示当前索引对应的元素在下一个数组中。
            // 生产者先存储元素，再链接下一个数组，再进行标记，最后更新生产者索引。
            // 因此当我们读取到该标记时，链接已完成，且可以安全的读取插入的元素。
            return newBufferPoll(buffer, index);
        }

        // Q: 队列一定为空吗？
        // A: 因为生产者先发布元素，再发布索引。因此如果element不可见，那么队列一定还是空。

        return null;
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
        final long offset = calcCircularRefElementOffset(index, mask);
        final Object e = lvRefElement(buffer, offset);
        if (e == JUMP)
        {
            // 当前元素为跳点标记，表示当前元素在下一个数组中，因此从下一个数组中peek
            return newBufferPeek(buffer, index);
        }

        // element 为null，或为普通元素
        return (E) e;
    }

    /**
     * 触发了扩容，将旧数组链接到新数组，并插入给定元素
     *
     * @param currIndex   生产者索引
     * @param oldBuffer   生产者当前使用的数组
     * @param offset      生产者索引对应的元素在旧数组中的偏移量
     * @param newBuffer   需要链接的下一个数组
     * @param offsetInNew 生产者索引对应的元素在新数组中的偏移量（如果新数组的大小和当前数组的大小不同，那么偏移量也不同）
     * @param e           待插入的元素
     */
    final void linkOldToNew(
        final long currIndex,
        final E[] oldBuffer, final long offset,
        final E[] newBuffer, final long offsetInNew,
        final E e)
    {
        // 1. 新元素添加到了新数组中，此时尚不可达 - 该步可使用Plain模式存储，因为在标记JUMP前不可达
        // 2. 旧数组链接到新数组，此时新数组也尚不可达 - 该步可使用Plain模式存储，因为在标记JUMP前不可达
        // 3. 添加JUMP到旧数组，此时JUMP对消费者可见，当消费者读取到JUMP时，知道链接已完成，且下一个元素也完成了存储。
        // 4. 更新生产者索引

        soRefElement(newBuffer, offsetInNew, e);
        // 链接到下一个buffer，并把下一个buffer的指针作为旧数组的一个元素
        // link to next buffer and add next indicator as element of old buffer
        soNext(oldBuffer, newBuffer);

        // 这里同offer一致，先更新element，再更新索引 - 因此也会遇见相同的时序问题。
        // 注意：在发布JUMP之前，前面的数据其实是不可达的，因此前面的写操作可以使用Plain模式

        soRefElement(oldBuffer, offset, JUMP);
        // index is visible after elements (isEmpty/poll ordering)
        soProducerIndex(currIndex + 1);// this ensures atomic write of long on 32bit platforms
    }

    /**
     *
     * @param buffer 生产者当前使用的数组
     * @param e      待插入的元素
     * @param index  生产者索引
     * @param offset 生产者索引对应的元素在数组中的偏移量
     */
    final void writeToQueue(final E[] buffer, final E e, final long index, final long offset)
    {
        // 注意这里的时序：先发布元素，再更新的索引
        // Ordered模式确保安全发布，当消费者读取到该对象时，便可以消费，而不必等待生产者索引可见（FastFlow）
        soRefElement(buffer, offset, e);
        // Ordered模式保证原子存储，以及当索引更新时，可确保元素已填充（内存屏障）
        soProducerIndex(index + 1);// this ensures atomic write of long on 32bit platforms
    }

    /**
     * 从下一个数组中peek元素。
     *
     * @param buffer 当前数组
     * @param index  当前生产者索引
     * @return element
     */
    private E newBufferPeek(final E[] buffer, final long index)
    {
        // 更新当前消费数组为下一个数组，同时更新对应的掩码
        E[] nextBuffer = lvNextArrayAndUnlink(buffer);
        consumerBuffer = nextBuffer;
        final long mask = length(nextBuffer) - 2;
        consumerMask = mask;

        // 基于linkOldToNew的设计，这里一定不为null，如果为null，则可能是supplier返回了null
        final long offset = calcCircularRefElementOffset(index, mask);
        return lvRefElement(nextBuffer, offset);
    }

    private E newBufferPoll(final E[] buffer, final long index)
    {
        // 更新当前消费数组为下一个数组，同时更新对应的掩码
        E[] nextBuffer = lvNextArrayAndUnlink(buffer);
        consumerBuffer = nextBuffer;
        final long mask = length(nextBuffer) - 2;
        consumerMask = mask;
        final long offset = calcCircularRefElementOffset(index, mask);
        final E n = lvRefElement(nextBuffer, offset);
        if (null == n)
        {
            // 基于linkOldToNew的设计，这里一定不为null，如果为null，则可能是supplier返回了null
            throw new IllegalStateException("new buffer must have at least one element");
        }
        else
        {
            // 注意和poll相同的时序，先更新的索引，再清理的元素 - 其实颠倒顺序更好
            soConsumerIndex(index + 1);// this ensures correctness on 32bit platforms
            soRefElement(nextBuffer, offset, null);
            return n;
        }
    }
}
