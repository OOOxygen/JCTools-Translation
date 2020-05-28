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

import org.jctools.util.RangeUtil;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.util.PortableJvmInfo.CPUs;
import static org.jctools.util.Pow2.isPowerOfTwo;
import static org.jctools.util.Pow2.roundToPowerOfTwo;

/**
 * Use a set number of parallel MPSC queues to diffuse the contention on tail.
 */
abstract class MpscCompoundQueueL0Pad<E> extends AbstractQueue<E> implements MessagePassingQueue<E>
{
    /**
     * 缓存行填充，避免{@link MpscCompoundQueueColdFields}上的属性产生伪共享
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

abstract class MpscCompoundQueueColdFields<E> extends MpscCompoundQueueL0Pad<E>
{
    // must be power of 2
    protected final int parallelQueues;
    protected final int parallelQueuesMask;
    protected final MpscArrayQueue<E>[] queues;

    /**
     *
     * @param capacity         队列容量上限
     * @param queueParallelism 并行度（子队列数）
     */
    @SuppressWarnings("unchecked")
    MpscCompoundQueueColdFields(int capacity, int queueParallelism)
    {
        parallelQueues = isPowerOfTwo(queueParallelism) ? queueParallelism
            : roundToPowerOfTwo(queueParallelism) / 2;
        parallelQueuesMask = parallelQueues - 1;
        queues = new MpscArrayQueue[parallelQueues];
        int fullCapacity = roundToPowerOfTwo(capacity);
        RangeUtil.checkGreaterThanOrEqual(fullCapacity, parallelQueues, "fullCapacity");
        for (int i = 0; i < parallelQueues; i++)
        {
            // 容量均摊到子队列
            queues[i] = new MpscArrayQueue<E>(fullCapacity / parallelQueues);
        }
    }
}

abstract class MpscCompoundQueueMidPad<E> extends MpscCompoundQueueColdFields<E>
{
    /**
     * 缓存行填充，避免前面属性和{@code consumerQueueIndex}上产生伪共享
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

    public MpscCompoundQueueMidPad(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }
}

abstract class MpscCompoundQueueConsumerQueueIndex<E> extends MpscCompoundQueueMidPad<E>
{
    /**
     * 消费者当前消费的队列的索引。
     */
    int consumerQueueIndex;

    MpscCompoundQueueConsumerQueueIndex(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }
}

/**
 * 由多个{@link MpscArrayQueue}构成的复合队列，旨在使用多个{@link MpscArrayQueue}分散生产者，降低其竞争。
 * <p>
 * 这个类也是个挺有意思的类，不过实际效果如何不一定比{@link MpscArrayQueue}好，比较看脸（{@link Thread#getId()}），
 * 如果生产者之间的id是连续的，经过'&'运算后，会落在不同的队列，应该会获得较好的效果，如果id较为分散，则不一定了。
 * 极限情况下：所有生产者都从同一个队列开始插入。
 * <p>
 * 警告：如果任务之间有依赖（比如时序要求），那么慎用该队列。
 */
public class MpscCompoundQueue<E> extends MpscCompoundQueueConsumerQueueIndex<E>
{
    /**
     * 缓存行填充，避免{@code consumerQueueIndex}上产生伪共享
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

    public MpscCompoundQueue(int capacity)
    {
        this(capacity, CPUs);
    }

    public MpscCompoundQueue(int capacity, int queueParallelism)
    {
        super(capacity, queueParallelism);
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现提示：
     * 1. 根据线程id计算首选队列，并尝试插入。
     * 2. 如果插入成功则结束，插入失败则尝试插入到其它队列（循环一圈）。
     * 3. 如果每个队列都插入失败则彻底失败。
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final int parallelQueuesMask = this.parallelQueuesMask;
        int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        final MpscArrayQueue<E>[] queues = this.queues;
        if (queues[start].offer(e))
        {
            return true;
        }
        else
        {
            return slowOffer(queues, parallelQueuesMask, start + 1, e);
        }
    }

    /**
     * 实现提示：
     * 以循环的方式尝试在各个队列使用{@link MpscArrayQueue#failFastOffer(Object)}压入，压入成功则返回，
     * 如果压入失败，则更新累计状态值，如果一圈下来，所有队列都表示已满，则返回失败，否则继续重试。
     */
    private boolean slowOffer(MpscArrayQueue<E>[] queues, int parallelQueuesMask, int start, E e)
    {
        final int queueCount = parallelQueuesMask + 1;
        final int end = start + queueCount;
        while (true)
        {
            // 每次循环开始，重置状态值，这个名字起的不算好（实际是想确定已满队列个数）
            int status = 0;
            for (int i = start; i < end; i++)
            {
                int s = queues[i & parallelQueuesMask].failFastOffer(e);
                if (s == 0)
                {
                    return true;
                }
                // 注意：如果是CAS失败，返回的是-1，因此status既会增加也会减少
                status += s;
            }

            if (status == queueCount)
            {
                // status == queueCount 意味着每个队列都返回1，即每个队列都已满
                // 如果一圈下来，所有队列都返回已满，则队列失败。
                return false;
            }
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现提示：消费者从当前队列索引开始，尝试从每个队列poll元素，如果poll成功，则更新{@link #consumerQueueIndex}，然后返回元素。
     * 如果一圈下来都失败了，也更新{@link #consumerQueueIndex}。
     */
    @Override
    public E poll()
    {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            e = queues[qIndex & parallelQueuesMask].poll();
            if (e != null)
            {
                break;
            }
        }
        // 无论成功还是失败，都更新索引，下次消费时仍从该索引开始
        consumerQueueIndex = qIndex;
        return e;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 实现提示：消费者从当前队列索引开始，尝试从每个队列peek元素，如果peek成功，则更新{@link #consumerQueueIndex}，然后返回元素。
     * 如果一圈下来都失败了，也更新{@link #consumerQueueIndex}。
     */
    @Override
    public E peek()
    {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            e = queues[qIndex & parallelQueuesMask].peek();
            if (e != null)
            {
                break;
            }
        }
        // 无论成功还是失败，都更新索引，下次消费时仍从该索引开始
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public int size()
    {
        int size = 0;
        for (MpscArrayQueue<E> lane : queues)
        {
            size += lane.size();
        }
        return size;
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final int parallelQueuesMask = this.parallelQueuesMask;
        int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        final MpscArrayQueue<E>[] queues = this.queues;
        if (queues[start].failFastOffer(e) == 0)
        {
            // 在第一个队列上插入成功，则直接返回
            return true;
        }
        else
        {
            // 在第一个队列上已经失败，只需要在其它队列尝试一遍
            // we already offered to first queue, try the rest
            for (int i = start + 1; i < start + parallelQueuesMask + 1; i++)
            {
                if (queues[i & parallelQueuesMask].failFastOffer(e) == 0)
                {
                    return true;
                }
            }
            // 因为是relaxedOffer，因此我们可能以任意的原因失败
            // this is a relaxed offer, we can fail for any reason we like
            return false;
        }
    }

    @Override
    public E relaxedPoll()
    {
        // 请查看poll的实现说明，这里的区别就是调用的是子队列的relaxedPoll
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            e = queues[qIndex & parallelQueuesMask].relaxedPoll();
            if (e != null)
            {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        // 请查看peek的实现说明，这里的区别就是调用的是子队列的relaxedPeek
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++)
        {
            e = queues[qIndex & parallelQueuesMask].relaxedPeek();
            if (e != null)
            {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }

    @Override
    public int capacity()
    {
        return queues.length * queues[0].capacity();
    }


    @Override
    public int drain(Consumer<E> c)
    {
        final int limit = capacity();
        return drain(c, limit);
    }

    @Override
    public int fill(Supplier<E> s)
    {

        return MessagePassingQueueUtil.fillBounded(this, s);
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

        final int parallelQueuesMask = this.parallelQueuesMask;
        int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        final MpscArrayQueue<E>[] queues = this.queues;
        int filled = queues[start].fill(s, limit);
        if (filled == limit)
        {
            // 在第一个队列上已经填充了期望的元素，则结束
            return limit;
        }
        else
        {
            // 在第一个队列上已经失败，只需要在其它队列尝试一遍（填充余下数量的元素）
            // we already offered to first queue, try the rest
            for (int i = start + 1; i < start + parallelQueuesMask + 1; i++)
            {
                filled += queues[i & parallelQueuesMask].fill(s, limit - filled);
                if (filled == limit)
                {
                    return limit;
                }
            }
            // 因为是relaxedOffer，因此我们可能以任意的原因失败
            // this is a relaxed offer, we can fail for any reason we like
            return filled;
        }
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
}
