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

abstract class SpscArrayQueueColdField<E> extends ConcurrentCircularArrayQueue<E>
{
    public static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.spsc.max.lookahead.step", 4096);
    /**
     * producerLimit的更新使用的观望步数（不太好直译）。
     * <p>
     * Q: 这是个什么神奇的优化？
     * A: 其关键在于{@code SpscArrayQueue.offerSlowPath}方法。
     * 生产者根据element是否为null判断是否可以填充该槽位，而不是判断{@code producerIndex}与{@code consumerIndex}的大小关系。
     * 在进行观望时，可以单步观望，也可以观望的远一点。这里假设了观望一段数据的性能好于单步观望，因此有了该设计。
     * <p>
     * Q: 为什么不使用capacity?
     * A: 观望步数越小，该设计的意义越小，越接近capacity就越容易失败，1/4可能是他们总结的一个经验值或理论值。
     */
    final int lookAheadStep;

    SpscArrayQueueColdField(int capacity)
    {
        super(capacity);
        lookAheadStep = Math.min(capacity() / 4, MAX_LOOK_AHEAD_STEP);
    }
}

abstract class SpscArrayQueueL1Pad<E> extends SpscArrayQueueColdField<E>
{
    /**
     * 缓存行填充，保护{@link ConcurrentCircularArrayQueue}中的的数据和{@code producerIndex}{@code producerLimit}
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

    SpscArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

// $gen:ordered-fields
abstract class SpscArrayQueueProducerIndexFields<E> extends SpscArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(SpscArrayQueueProducerIndexFields.class, "producerIndex");

    /**
     * 生产者索引(生产者的进度)。
     * 目前的实现是：生产者先填充元素，再更新索引。
     */
    private volatile long producerIndex;
    /**
     * 在重新读取消费者索引之前，第一个不可用的生产者索引。
     * <p>
     * 注意：这个值是观望element是否为null计算出来的，而不是根据{@code consumerIndex}计算出来的。
     * <p>
     * Q: 该值与{@link #producerIndex}在一起有什么好处？
     * A: 在单生产者模型下，加载这两个值中任何一个都有机会将另一个同时加载到缓存行中，从而提高读效率。
     */
    protected long producerLimit;

    SpscArrayQueueProducerIndexFields(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileProducerIndex
     * 当不确定是生产者时，使用该方法加载索引
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * loadPlainProducerIndex
     * 当确定是生产者时，使用该方法加载索引即可（因为只有生产者线程修改该索引，因此生产者线程不必使用volatile模式读）
     */
    final long lpProducerIndex()
    {
        return UNSAFE.getLong(this, P_INDEX_OFFSET);
    }

    /**
     * storeOrderedProducerIndex
     * 需要保证存储的原子性，以及当其它线程看见该值时能确定元素填充。
     */
    final void soProducerIndex(final long newValue)
    {
        UNSAFE.putOrderedLong(this, P_INDEX_OFFSET, newValue);
    }

}

abstract class SpscArrayQueueL2Pad<E> extends SpscArrayQueueProducerIndexFields<E>
{
    /**
     * 缓存行填充，避免{@code producerIndex}{@code producerLimit}{@code consumerIndex}产生伪共享
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

    SpscArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class SpscArrayQueueConsumerIndexField<E> extends SpscArrayQueueL2Pad<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(SpscArrayQueueConsumerIndexField.class, "consumerIndex");

    /**
     * 消费者索引
     * 目前的实现是：消费者先消费元素(将槽位上的元素置为null)，再更新索引。
     */
    private volatile long consumerIndex;

    SpscArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileConsumerIndex
     * 当不确定是消费者线程时，需要使用该方法读取
     */
    public final long lvConsumerIndex()
    {
        // 为啥不直接读....
        return UNSAFE.getLongVolatile(this, C_INDEX_OFFSET);
    }

    /**
     * loadPlainConsumerIndex
     * 当确定是消费者的情况下，可以以普通模式读取，因为只有消费者更新该索引
     */
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    /**
     * storeOrderedConsumerIndex
     * 消费者更新索引时，需要保证原子存储，以及当其它线程看见该值时能确定元素已消费。
     */
    final void soConsumerIndex(final long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class SpscArrayQueueL3Pad<E> extends SpscArrayQueueConsumerIndexField<E>
{
    /**
     * 缓存行填充，避免{@code consumerIndex}产生伪共享
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

    SpscArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}


/**
 * 底层为预分配数组的<b>单生产者单消费者的</b>队列。
 * 此实现是Fast Flow算法的混搭，其优化方法取自BQueue算法（Fast Flow的一种变体），并经过调整以符合Queue.offer关于容量的语义。
 * <p>
 * 在Fast Flow模型下：
 * 1. 当消费者发现element存在时，就会进行消费，而不会等待生产者索引可见，可能导致消费者索引超过生产者索引。
 * 因此在{@link IndexedQueueSizeUtil}对size和isEmpty都做了特殊处理。
 * 2. 当生产者发现element为null时，就会进行填充，而不会等待消费者索引可见，因此size可能超过capacity（已提交，但尚未修复，需要修改size计算）。
 * <p>
 * 优点：这样可以减少了读取对方索引的情况，从而减少缓存行miss问题，从而改善读性能。
 *
 * A Single-Producer-Single-Consumer queue backed by a pre-allocated buffer.
 * <p>
 * This implementation is a mashup of the <a href="http://sourceforge.net/projects/mc-fastflow/">Fast Flow</a>
 * algorithm with an optimization of the offer method taken from the <a
 * href="http://staff.ustc.edu.cn/~bhua/publications/IJPP_draft.pdf">BQueue</a> algorithm (a variation on Fast
 * Flow), and adjusted to comply with Queue.offer semantics with regards to capacity.<br>
 * For convenience the relevant papers are available in the `resources` folder:<br>
 * <i>
 *     2010 - Pisa - SPSC Queues on Shared Cache Multi-Core Systems.pdf<br>
 *     2012 - Junchang- BQueue- Efﬁcient and Practical Queuing.pdf <br>
 * </i>
 * This implementation is wait free.
 */
public class SpscArrayQueue<E> extends SpscArrayQueueL3Pad<E>
{

    public SpscArrayQueue(final int capacity)
    {
        super(Math.max(capacity, 4));
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single producer thread use only.
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // 加载为本地变量，避免在volatile读之后重复加载
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final long producerIndex = this.lpProducerIndex();

        // producerIndex >= producerLimit 表示队列已满或当前的缓存值已过期，需要读取最新的消费者索引
        // offerSlowPath() 如果返回true则表示队列未满，返回false则表示队列已满

        if (producerIndex >= producerLimit &&
            !offerSlowPath(buffer, mask, producerIndex))
        {
            return false;
        }
        final long offset = calcCircularRefElementOffset(producerIndex, mask);

        // 注意这里的时序问题，先发布发元素，再发布的索引。
        // 提示：在Fast Flow模型下，消费者会在element可见时就消费，而不会等待生产者索引更新，因此这里不可以使用Plain模式存储。
        soRefElement(buffer, offset, e);
        soProducerIndex(producerIndex + 1); // ordered store -> atomic and ordered for size()
        return true;
    }

    /**
     * 这是理解{@link #lookAheadStep}设计的关键。
     * 观望，避免了读取消费者索引。
     */
    private boolean offerSlowPath(final E[] buffer, final long mask, final long producerIndex)
    {
        final int lookAheadStep = this.lookAheadStep;
        if (null == lvRefElement(buffer,
            calcCircularRefElementOffset(producerIndex + lookAheadStep, mask)))
        {
            // 观望了一段数据，这段数据都为null，则这段数据都可以用于发布，则更新producerLimit
            producerLimit = producerIndex + lookAheadStep;
        }
        else
        {
            // 这段数据不都为null，此时有两种选择：1.读取消费者索引 2. 缩小观望范围
            // 这里是采用的单步观望，而不是读取消费者索引
            final long offset = calcCircularRefElementOffset(producerIndex, mask);
            if (null != lvRefElement(buffer, offset))
            {
                // 当前索引的元素尚未被消费(或清除操作尚不可见)，则表示队列已满，无法填充
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E poll()
    {
        final long consumerIndex = this.lpConsumerIndex();
        final long offset = calcCircularRefElementOffset(consumerIndex, mask);
        // 加载为本地变量，避免在volatile读之后重复加载
        // local load of field to avoid repeated loads after volatile reads
        final E[] buffer = this.buffer;
        final E e = lvRefElement(buffer, offset);
        if (null == e)
        {
            // Q: 为什么可以在元素尚不可见时就直接返回？
            // A: 因为生产者是先发布元素，再更新索引，因此当元素为Null的时候，在这之前队列一定为空，因此是满足poll的语义的。
            return null;
        }
        // 注意：由于生产者是观望element是否null，以进行下一步的，因此这里使用Ordered模式可以使其更快感知到。
        // 注意：由于未等待生产者索引可见，因此这里可能导致消费者索引超过生产者索引。
        // 这里理论上可以使用Plain模式清理元素
        soRefElement(buffer, offset, null);
        soConsumerIndex(consumerIndex + 1); // ordered store -> atomic and ordered for size()
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation is correct for single consumer thread use only.
     */
    @Override
    public E peek()
    {
        // 由于是单消费者，因此加载的元素一定对应关联的consumerIndex
        return lvRefElement(buffer, calcCircularRefElementOffset(lpConsumerIndex(), mask));
    }

    @Override
    public boolean relaxedOffer(final E message)
    {
        // 这里调用offer是合适的，因此offer本身就很轻量级
        return offer(message);
    }

    @Override
    public E relaxedPoll()
    {
        // 这里调用poll是合适的，因为poll本身很轻量级
        return poll();
    }

    @Override
    public E relaxedPeek()
    {
        // 这里调用peek是合适的，因为poll本身很轻量级
        return peek();
    }

    @Override
    public int drain(final Consumer<E> c)
    {
        return drain(c, capacity());
    }

    @Override
    public int fill(final Supplier<E> s)
    {
        return fill(s, capacity());
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
        final long consumerIndex = this.lpConsumerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long index = consumerIndex + i;
            final long offset = calcCircularRefElementOffset(index, mask);
            final E e = lvRefElement(buffer, offset);
            if (null == e)
            {
                // 元素为null，则队列为空（因为生产者先填充元素，再更新索引）
                return i;
            }
            // 注意：由于生产者是观望element是否null，以进行下一步的，因此这里使用Ordered模式可以使其更快感知到。
            // 注意：由于未等待生产者索引可见，因此这里可能导致消费者索引超过生产者索引。
            soRefElement(buffer, offset, null);
            soConsumerIndex(index + 1); // ordered store -> atomic and ordered for size()
            c.accept(e);
        }
        return limit;
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
        final int lookAheadStep = this.lookAheadStep;
        final long producerIndex = this.lpProducerIndex();

        for (int i = 0; i < limit; i++)
        {
            final long index = producerIndex + i;
            final long lookAheadElementOffset =
                calcCircularRefElementOffset(index + lookAheadStep, mask);
            if (null == lvRefElement(buffer, lookAheadElementOffset))
            {
                // 观望成功，表明这一段都为null，都可以填充。
                int lookAheadLimit = Math.min(lookAheadStep, limit - i);
                for (int j = 0; j < lookAheadLimit; j++)
                {
                    // 与offer保持相同的时序
                    final long offset = calcCircularRefElementOffset(index + j, mask);
                    soRefElement(buffer, offset, s.get());
                    soProducerIndex(index + j + 1); // ordered store -> atomic and ordered for size()
                }
                i += lookAheadLimit - 1;
            }
            else
            {
                // 这段数据不都为null，此时有两种选择：1.读取消费者索引 2. 缩小观望范围
                // 这里是采用的单步观望，而不是读取消费者索引
                final long offset = calcCircularRefElementOffset(index, mask);
                if (null != lvRefElement(buffer, offset))
                {
                    return i;
                }
                // 与offer保持相同的时序
                soRefElement(buffer, offset, s.get());
                soProducerIndex(index + 1); // ordered store -> atomic and ordered for size()
            }

        }
        return limit;
    }

    @Override
    public void drain(final Consumer<E> c, final WaitStrategy w, final ExitCondition exit)
    {
        if (null == c)
            throw new IllegalArgumentException("c is null");
        if (null == w)
            throw new IllegalArgumentException("wait is null");
        if (null == exit)
            throw new IllegalArgumentException("exit condition is null");

        final E[] buffer = this.buffer;
        final long mask = this.mask;
        long consumerIndex = this.lpConsumerIndex();

        int counter = 0;
        while (exit.keepRunning())
        {
            for (int i = 0; i < 4096; i++)
            {
                final long offset = calcCircularRefElementOffset(consumerIndex, mask);
                final E e = lvRefElement(buffer, offset);
                if (null == e)
                {
                    // 队列为空，使用等待策略进行等待
                    counter = w.idle(counter);
                    continue;
                }
                consumerIndex++;
                counter = 0;
                // 与poll保持相同的时序
                soRefElement(buffer, offset, null);
                soConsumerIndex(consumerIndex); // ordered store -> atomic and ordered for size()
                c.accept(e);
            }
        }
    }

    @Override
    public void fill(final Supplier<E> s, final WaitStrategy w, final ExitCondition e)
    {
        if (null == w)
            throw new IllegalArgumentException("waiter is null");
        if (null == e)
            throw new IllegalArgumentException("exit condition is null");
        if (null == s)
            throw new IllegalArgumentException("supplier is null");

        final E[] buffer = this.buffer;
        final long mask = this.mask;
        final int lookAheadStep = this.lookAheadStep;
        long producerIndex = this.lpProducerIndex();
        int counter = 0;
        while (e.keepRunning())
        {
            final long lookAheadElementOffset =
                calcCircularRefElementOffset(producerIndex + lookAheadStep, mask);
            if (null == lvRefElement(buffer, lookAheadElementOffset))
            {
                // 观望成功，表明这一段都为null，都可以填充
                for (int j = 0; j < lookAheadStep; j++)
                {
                    final long offset = calcCircularRefElementOffset(producerIndex, mask);
                    producerIndex++;
                    soRefElement(buffer, offset, s.get());
                    soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
                }
            }
            else
            {
                // 这段数据不都为null，此时有两种选择：1.读取消费者索引 2. 缩小观望范围
                // 这里是采用的单步观望，而不是读取消费者索引
                final long offset = calcCircularRefElementOffset(producerIndex, mask);
                if (null != lvRefElement(buffer, offset))
                {
                    counter = w.idle(counter);
                    continue;
                }
                producerIndex++;
                counter = 0;
                // 与poll保持相同的时序
                soRefElement(buffer, offset, s.get());
                soProducerIndex(producerIndex); // ordered store -> atomic and ordered for size()
            }
        }
    }
}
