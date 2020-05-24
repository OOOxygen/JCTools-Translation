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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeLongArrayAccess.*;
import static org.jctools.util.UnsafeRefArrayAccess.*;

abstract class MpmcArrayQueueL1Pad<E> extends ConcurrentSequencedCircularArrayQueue<E>
{
    /**
     * 缓冲行填充 - 避免{@link #buffer} {@link #sequenceBuffer}和{@code producerIndex}产生伪共享
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

    MpmcArrayQueueL1Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpmcArrayQueueProducerIndexField<E> extends MpmcArrayQueueL1Pad<E>
{
    private final static long P_INDEX_OFFSET = fieldOffset(MpmcArrayQueueProducerIndexField.class, "producerIndex");

    /**
     * 生产者索引。
     * 这是一个预更新值，看{@link #casProducerIndex(long, long)}就能知道。生产者们先竞争更新索引，再填充元素。
     */
    private volatile long producerIndex;

    MpmcArrayQueueProducerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileProducerIndex
     * 多生产者模型，都需要读取最新的索引
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * 由于是多生产者，因此生产者需要CAS原子方式更新索引。
     */
    final boolean casProducerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, P_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpmcArrayQueueL2Pad<E> extends MpmcArrayQueueProducerIndexField<E>
{
    /**
     * 缓存行填充 - 避免{@code consumerIndex}和{@code producerIndex}之间产生伪共享
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

    MpmcArrayQueueL2Pad(int capacity)
    {
        super(capacity);
    }
}

//$gen:ordered-fields
abstract class MpmcArrayQueueConsumerIndexField<E> extends MpmcArrayQueueL2Pad<E>
{
    private final static long C_INDEX_OFFSET = fieldOffset(MpmcArrayQueueConsumerIndexField.class, "consumerIndex");

    /**
     * 消费者索引
     * 这也是一个预更新值，看{@link #casProducerIndex(long, long)}就可以知道。消费者们先竞争更新索引，更新成功的线程可以消费该索引对应的元素。
     */
    private volatile long consumerIndex;

    MpmcArrayQueueConsumerIndexField(int capacity)
    {
        super(capacity);
    }

    /**
     * loadVolatileConsumerIndex
     * 因为是多消费者模型，都需要读取最新值
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * 由于是多消费者模式，因此消费者们需要CAS原子方式更新索引。
     */
    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }
}

abstract class MpmcArrayQueueL3Pad<E> extends MpmcArrayQueueConsumerIndexField<E>
{
    /**
     * 缓存行填充 - 避免{@code consumerIndex}产生伪共享
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

    MpmcArrayQueueL3Pad(int capacity)
    {
        super(capacity);
    }
}

/**
 * 基于{@link org.jctools.queues.ConcurrentCircularArrayQueue}的多生产者多消费者队列。
 * 这意味着任何线程和所有线程都可以调用offer/poll/peek方法，并保持正确性。<br>
 * 此实现遵循在包级别记录的的用于避免伪共享的模式（缓存行填充）。<br>
 * offer/poll的算法是D.Vyukov提出的[有界多生产者多消费者队列]算法的适配。br>
 * <p>
 * 记住以下权衡：
 * <ol>
 * <li>填充避免伪共享：索引字段和两个数组的两侧都进行了填充。 我们消耗内存以避免（主动和被动的）伪共享</li>
 * <li>2个数组，而不是一个：算法需要一个额外的long数组，该数组与elements数组的大小匹配。 这是为缓冲区分配的内存的两倍/三倍</li>
 * <li>容量为2的幂：实际元素buffer（和sequence buffer）的容量是2的最接近的幂，大于或等于请求的容量。</li>
 * </ol>
 * <p>
 *
 * A Multi-Producer-Multi-Consumer queue based on a {@link org.jctools.queues.ConcurrentCircularArrayQueue}. This
 * implies that any and all threads may call the offer/poll/peek methods and correctness is maintained. <br>
 * This implementation follows patterns documented on the package level for False Sharing protection.<br>
 * The algorithm for offer/poll is an adaptation of the one put forward by D. Vyukov (See <a
 * href="http://www.1024cores.net/home/lock-free-algorithms/queues/bounded-mpmc-queue">here</a>). The original
 * algorithm uses an array of structs which should offer nice locality properties but is sadly not possible in
 * Java (waiting on Value Types or similar). The alternative explored here utilizes 2 arrays, one for each
 * field of the struct. There is a further alternative in the experimental project which uses iteration phase
 * markers to achieve the same algo and is closer structurally to the original, but sadly does not perform as
 * well as this implementation.<br>
 * <p>
 * Tradeoffs to keep in mind:
 * <ol>
 * <li>Padding for false sharing: counter fields and queue fields are all padded as well as either side of
 * both arrays. We are trading memory to avoid false sharing(active and passive).
 * <li>2 arrays instead of one: The algorithm requires an extra array of longs matching the size of the
 * elements array. This is doubling/tripling the memory allocated for the buffer.
 * <li>Power of 2 capacity: Actual elements buffer (and sequence buffer) is the closest power of 2 larger or
 * equal to the requested capacity.
 * </ol>
 */
public class MpmcArrayQueue<E> extends MpmcArrayQueueL3Pad<E>
{
    public static final int MAX_LOOK_AHEAD_STEP = Integer.getInteger("jctools.mpmc.max.lookahead.step", 4096);
    /**
     * 来了，来了，它又来了！
     * Q: 观望步数（不太好直译）？这是个什么东西？
     * A: 这里是设计和{@link SpscArrayQueue}还不太一样，这里是用于减少潜在的冲突的。
     */
    private final int lookAheadStep;

    public MpmcArrayQueue(final int capacity)
    {
        super(RangeUtil.checkGreaterThanOrEqual(capacity, 2, "capacity"));
        lookAheadStep = Math.max(2, Math.min(capacity() / 4, MAX_LOOK_AHEAD_STEP));
    }

    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // 读取为本地变量，避免在接下来的volatile读之后重新读取
        final long mask = this.mask;
        final long capacity = mask + 1;
        final long[] sBuffer = sequenceBuffer;

        // seq pIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == pIndex 表示该槽位应该被填充（此时竞争更新生产者索引）
        // seq < pIndex  表示该槽位尚未被消费，队列已满
        // seq > pIndex  表示该槽位已经被填充（初始为i, 填充之后 + 1）或已被消费（消费之后 + capacity）需要再下一环才能填充，此时需要重试

        long pIndex;
        long seqOffset;
        long seq;
        long cIndex = Long.MIN_VALUE;// start with bogus value, hope we don't need it
        do
        {
            pIndex = lvProducerIndex();
            seqOffset = calcCircularLongElementOffset(pIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            // consumer has not moved this seq forward, it's as last producer left
            if (seq < pIndex)
            {
                // 根据seq推断该槽位尚未被消费，队列已满，由于消费者先更新的索引，后进行消费，最后更新seq，
                // 因此需要读取最新的消费者索引查看是否有消费者正在消费该槽位，如果有则需要等待，以满足Queue对offer的语义要求。
                // Extra check required to ensure [Queue.offer == false iff queue is full]
                if (pIndex - capacity >= cIndex && // test against cached cIndex
                    pIndex - capacity >= (cIndex = lvConsumerIndex()))
                { // test against latest cIndex
                    // 读取最新的消费者索引后，发现队列确实已满
                    return false;
                }
                else
                {
                    // 队列并未真的满（消费者正在消费），此时需要重试，为了避免CAS调用，令seq大于pIndex （seq会在下一轮重新初始化，因此是安全的）
                    seq = pIndex + 1; // (+) hack to make it go around again without CAS
                }
            }
            // seq >= pIndex 请查看前面的大小关系注释
        }
        while (seq > pIndex || // another producer has moved the sequence(or +)
            !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

        // Q: 为什么必须等待seq为期望值？
        // A: 只有当seq为期望值时，可保证元素为null，且在seq上不会发生并发修改！（如果不等待seq为期望值，则在seq上可能产生并发修改）

        // 注意生产者的操作时序：先CAS更新生产者索引，再发布元素，最后更新seq - 消费必须等待seq可见，否则seq上可能产生并发修改。
        // seq是完成生产者与消费者通信的关键

        // 理论上这里可以使用Plain模存储，因为前面的CAS已经保证了正确的构造，可以安全的发布，且消费者依赖于seq可见
        soRefElement(buffer, calcCircularRefElementOffset(pIndex, mask), e);
        // 填充元素之后，将seq更新为pIndex + 1，需要保证原子存储，且存储元素不会重排序到该操作之后
        // seq++;
        soLongElement(sBuffer, seqOffset, pIndex + 1);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Because return null indicates queue is empty we cannot simply rely on next element visibility for poll
     * and must test producer index when next element is not visible.
     */
    @Override
    public E poll()
    {
        // local load of field to avoid repeated loads after volatile reads
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;

        // seq cIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == expectedSeq(cIndex + 1 ) 表示该槽位已经被填充（填充之后 + 1），可以被消费（此时竞争更新生产者索引）
        // seq > expectedSeq 表示已经被消费（消费之后 + capacity），此时需要重试
        // seq < expectedSeq 表示尚未被填充，因为seq最后对消费者可见，因此需要查看生产者索引，是否有生产者正在填充

        long cIndex;
        long seq;
        long seqOffset;
        long expectedSeq;
        long pIndex = -1; // start with bogus value, hope we don't need it
        do
        {
            cIndex = lvConsumerIndex();
            seqOffset = calcCircularLongElementOffset(cIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq)
            {
                // 元素尚未被填充（或正在填充但seq尚不可见），此时需要读取生产者索引，是否有生产者正在填充（队列是否真的为空）
                // slot has not been moved by producer
                if (cIndex >= pIndex && // test against cached pIndex
                    cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                {
                    // 严格地空检查，以满足Queue对poll的语义要求（当且仅当队列为空时才能返回null）
                    // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                    return null;
                }
                else
                {
                    // 队列不为空（生产者正在生产），因此需要重试，为避免CAS调用，令seq > expectedSeq（seq会在下一轮重新初始化，因此是安全的）
                    seq = expectedSeq + 1; // trip another go around
                }
            }
        }
        while (seq > expectedSeq || // another consumer beat us to it
            !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS

        // Q: 为什么必须等待seq为期望值？
        // A: 只有当seq为期望值时，可保证元素可见，且在seq上不会发生并发修改！（如果不等待seq为期望值，则在seq上可能产生并发修改）

        // 注意消费者的操作时序：先CAS更新consumerIndex，再删除元素，最后再更新seq - 生产者也必须等待seq可见，否则seq上可能产生并发修改。
        // seq是完成生产者与消费者通信的关键

        // 理论上这里是可以使用Plain模式清理元素，因为生产者必须等待seq为期望值时才能填充元素。
        final long offset = calcCircularRefElementOffset(cIndex, mask);
        final E e = lpRefElement(buffer, offset);
        soRefElement(buffer, offset, null);
        // 更新seq为下一环的序号，生产者在下一环的时候填充
        // i.e. seq += capacity
        soLongElement(sBuffer, seqOffset, cIndex + mask + 1);
        return e;
    }

    @Override
    public E peek()
    {
        // 这之前的版本中，可能peek到下一环的元素，我上报之后，他们进行了修复。
        // https://github.com/JCTools/JCTools/pull/295
        // 我想的是校验producerIndex或sequence，但是原作者选择的是校验consumerIndex。
        // Q：为什么不校验producerIndex？
        // A：作者是这样解释的，seq存在的意义就是为了减少对生产者索引的读，以避免缓存行miss问题，如果校验producerIndex，则可能触发
        // 大量的缓存行miss，因此不校验producerIndex。
        // 但是校验consumerIndex也不算完美，因为过于严格，我们其实只需要保证它不是一个覆盖值，却变成了必须是一个稳定值。
        // 看relaxedPeek会更容易理解该问题。

        // local load of field to avoid repeated loads after volatile reads
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;

        // seq cIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == expectedSeq(cIndex + 1 ) 表示该槽位已经被填充（填充之后 + 1），可以被消费（此时竞争更新生产者索引）
        // seq > expectedSeq 表示已经被消费（消费之后 + capacity），此时需要重试
        // seq < expectedSeq 表示尚未被填充，因为seq最后对消费者可见，因此需要查看生产者索引，是否有生产者正在填充

        long cIndex;
        long seq;
        long seqOffset;
        long expectedSeq;
        long pIndex = -1; // start with bogus value, hope we don't need it
        E e;
        do
        {
            cIndex = lvConsumerIndex();
            seqOffset = calcCircularLongElementOffset(cIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq)
            {
                // seq小于期望值，此时队列可能为空，或生产者正在填充元素，因此需要读取最新的生产者索引，以进行严格的空判断，以满足Queue对peek的语义要求
                // slot has not been moved by producer
                if (cIndex >= pIndex && // test against cached pIndex
                    cIndex == (pIndex = lvProducerIndex())) // update pIndex if we must
                {
                    // 严格地空检查，以满足Queue对peek的语义要求（当且仅当队列为空时才能返回null）
                    // strict empty check, this ensures [Queue.poll() == null iff isEmpty()]
                    return null;
                }
            }
            else if (seq == expectedSeq)
            {
                // 解释下：由于加载lvConsumerIndex和lvRefElement这是一个组合操作，
                // 在多消费者情况下，无法保证lvRefElement加载的element是属于这个索引的，可能读取到下一环的元素，因此需要校验。
                // 在加载该consumerIndex对应元素之后，如果消费者索引没有发生改变，那么证明这期间没有消费者消费，那么加载的元素就是我们期望的。
                // 时序很重要，这三个加载指令都不能重排序，因此都需要使用volatile语义，否则将无法校验（类似StampedLock的用法）

                final long offset = calcCircularRefElementOffset(cIndex, mask);
                e = lvRefElement(buffer, offset);
                if (lvConsumerIndex() == cIndex)
                    return e;
            }
        }
        while (true);
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        final long mask = this.mask;
        final long[] sBuffer = sequenceBuffer;

        // seq pIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == pIndex 表示该槽位应该被填充（此时竞争更新生产者索引）
        // seq < pIndex  表示该槽位尚未被消费，队列已满
        // seq > pIndex  表示该槽位已经被填充（初始为i, 填充之后 + 1）或已被消费（消费之后 + capacity）需要再下一环才能填充，此时需要重试

        long pIndex;
        long seqOffset;
        long seq;
        do
        {
            pIndex = lvProducerIndex();
            seqOffset = calcCircularLongElementOffset(pIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            if (seq < pIndex)
            { // slot not cleared by consumer yet
                // 根据seq推断推断已满，由于是relaxedOffer，因此不检查是否有消费者正在消费
                return false;
            }
        }
        while (seq > pIndex || // another producer has moved the sequence
            !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

        // 理论上这里可以使用Plain模式存储，因为前面的CAS已经保证了正确的构造，可以安全的发布，且消费者依赖于seq可见
        soRefElement(buffer, calcCircularRefElementOffset(pIndex, mask), e);
        soLongElement(sBuffer, seqOffset, pIndex + 1);
        return true;
    }

    @Override
    public E relaxedPoll()
    {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;

        // seq cIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == expectedSeq(cIndex + 1 ) 表示该槽位已经被填充（填充之后 + 1），可以被消费（此时竞争更新生产者索引）
        // seq > expectedSeq 表示已经被消费（消费之后 + capacity），此时需要重试
        // seq < expectedSeq 表示尚未被填充(或已经填充但seq尚不可见)，由于是relaxedPoll，因此可以返回null

        long cIndex;
        long seqOffset;
        long seq;
        long expectedSeq;
        do
        {
            cIndex = lvConsumerIndex();
            seqOffset = calcCircularLongElementOffset(cIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq)
            {
                // seq小于期望值，此时队列可能为空，也可能有生产者正在填充，由于是relaxedPoll，因此不检查是否有生产者正在填充
                return null;
            }
        }
        while (seq > expectedSeq || // another consumer beat us to it
            !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS
        // CAS竞争成功，可以消费该索引对应的元素
        // 理论上这里是可以使用Plain模式清理元素，因为生产者必须等待seq为期望值时才能填充元素。
        final long offset = calcCircularRefElementOffset(cIndex, mask);
        final E e = lpRefElement(buffer, offset);
        soRefElement(buffer, offset, null);
        soLongElement(sBuffer, seqOffset, cIndex + mask + 1);
        return e;
    }

    @Override
    public E relaxedPeek()
    {
        // local load of field to avoid repeated loads after volatile reads
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;

        // seq cIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == expectedSeq(cIndex + 1 ) 表示该槽位已经被填充（填充之后 + 1），可以被消费（此时竞争更新生产者索引）
        // seq > expectedSeq 表示已经被消费（消费之后 + capacity），此时需要重试
        // seq < expectedSeq 表示尚未被填充，因为seq最后对消费者可见，因此需要查看生产者索引，是否有生产者正在填充

        long cIndex;
        long seq;
        long seqOffset;
        long expectedSeq;
        E e;
        do
        {
            cIndex = lvConsumerIndex();
            seqOffset = calcCircularLongElementOffset(cIndex, mask);
            seq = lvLongElement(sBuffer, seqOffset);
            expectedSeq = cIndex + 1;
            if (seq < expectedSeq)
            {
                // seq小于期望值，此时队列可能为空，也可能有生产者正在填充，由于是relaxedPeek，因此不检查是否有生产者正在填充
                return null;
            }
            else if (seq == expectedSeq)
            {
                // 解释下：由于加载lvConsumerIndex和lvRefElement这是一个组合操作，
                // 在多消费者情况下，无法保证lvRefElement加载的element是属于这个索引的，可能读取到下一环的元素，因此需要校验。
                // 在加载该consumerIndex对应元素之后，如果消费者索引没有发生改变，那么证明这期间没有消费者消费，那么加载的元素就是我们期望的。
                // 时序很重要，这三个加载指令都不能重排序，因此都需要使用volatile语义，否则将无法校验（类似StampedLock的用法）

                final long offset = calcCircularRefElementOffset(cIndex, mask);
                e = lvRefElement(buffer, offset);
                if (lvConsumerIndex() == cIndex)
                    return e;
            }
        }
        while (true);
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

        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;
        final int maxLookAheadStep = Math.min(this.lookAheadStep, limit);
        int consumed = 0;

        while (consumed < limit)
        {
            final int remaining = limit - consumed;
            final int lookAheadStep = Math.min(remaining, maxLookAheadStep);
            final long cIndex = lvConsumerIndex();
            final long lookAheadIndex = cIndex + lookAheadStep - 1;
            final long lookAheadSeqOffset = calcCircularLongElementOffset(lookAheadIndex, mask);
            final long lookAheadSeq = lvLongElement(sBuffer, lookAheadSeqOffset);
            final long expectedLookAheadSeq = lookAheadIndex + 1;

            // lookAheadSeq == expectedLookAheadSeq 表示该元素已被填充，那么此次观望是成功的，
            // cIndex - lookAheadIndex这段元素可以被消费，如果此时CAS竞争成功，则当前消费者可以消费这一段数据
            // 如果竞争失败，则退化为一个元素一个元素地消费

            if (lookAheadSeq == expectedLookAheadSeq && casConsumerIndex(cIndex, expectedLookAheadSeq))
            {
                for (int i = 0; i < lookAheadStep; i++)
                {
                    final long index = cIndex + i;
                    final long seqOffset = calcCircularLongElementOffset(index, mask);
                    final long offset = calcCircularRefElementOffset(index, mask);
                    final long expectedSeq = index + 1;
                    // 必须等待seq为期望值（生产者已完成所有操作） - 只有当seq为期望值时，可保证元素可见，且在seq上不会发生并发修改。
                    while (lvLongElement(sBuffer, seqOffset) != expectedSeq)
                    {

                    }
                    final E e = lpRefElement(buffer, offset);
                    soRefElement(buffer, offset, null);
                    soLongElement(sBuffer, seqOffset, index + mask + 1);
                    // 注意Consumer中对accept方法约束 - 不可以跑出异常！
                    // 这里可以看到，如果抛出异常，剩余部分元素将永远不能被消费，从而导致队列状态被彻底破坏，再也无法工作。
                    c.accept(e);
                }
                consumed += lookAheadStep;
            }
            else
            {
                // 观望失败，lookAheadSeq < expectedLookAheadSeq 表示这一段数据未被填充完毕（可能部分已被填充）
                if (lookAheadSeq < expectedLookAheadSeq)
                {
                    // 判断cIndex对应的元素是否已经被填充了（cIndex表示当前要消费的元素索引），如果cIndex对应的元素尚未被填充，则证明队列为空
                    if (notAvailable(cIndex, mask, sBuffer, cIndex + 1))
                    {
                        return consumed;
                    }
                }
                // 退化为一个元素一个元素地消费
                return consumed + drainOneByOne(c, remaining);
            }
        }
        return limit;
    }

    private int drainOneByOne(Consumer<E> c, int limit)
    {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;

        // seq cIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == expectedSeq(cIndex + 1 ) 表示该槽位已经被填充（填充之后 + 1），可以被消费（此时竞争更新生产者索引）
        // seq > expectedSeq 表示已经被消费（消费之后 + capacity），此时需要重试
        // seq < expectedSeq 表示尚未被填充，因为seq最后对消费者可见，因此需要查看生产者索引，是否有生产者正在填充

        long cIndex;
        long seqOffset;
        long seq;
        long expectedSeq;
        for (int i = 0; i < limit; i++)
        {
            do
            {
                cIndex = lvConsumerIndex();
                seqOffset = calcCircularLongElementOffset(cIndex, mask);
                seq = lvLongElement(sBuffer, seqOffset);
                expectedSeq = cIndex + 1;
                if (seq < expectedSeq)
                {
                    // 元素尚未被填充（或正在填充但seq尚不可见），由于接口对drain的语义表述为relaxedPoll，因此不检查生产者索引
                    return i;
                }
            }
            while (seq > expectedSeq || // another consumer beat us to it
                !casConsumerIndex(cIndex, cIndex + 1)); // failed the CAS
            // CAS竞争成功，可以消费该索引对应的元素
            // 理论上这里是可以使用Plain模式清理元素，因为生产者必须等待seq为期望值时才能填充元素。
            final long offset = calcCircularRefElementOffset(cIndex, mask);
            final E e = lpRefElement(buffer, offset);
            soRefElement(buffer, offset, null);
            soLongElement(sBuffer, seqOffset, cIndex + mask + 1);
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

        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;
        final int maxLookAheadStep = Math.min(this.lookAheadStep, limit);
        int produced = 0;

        while (produced < limit)
        {
            final int remaining = limit - produced;
            final int lookAheadStep = Math.min(remaining, maxLookAheadStep);
            final long pIndex = lvProducerIndex();
            final long lookAheadIndex = pIndex + lookAheadStep - 1;
            final long lookAheadSeqOffset = calcCircularLongElementOffset(lookAheadIndex, mask);
            final long lookAheadSeq = lvLongElement(sBuffer, lookAheadSeqOffset);
            final long expectedLookAheadSeq = lookAheadIndex;

            // lookAheadSeq == expectedLookAheadSeq 表述元素已经被消费，观望成功，
            // pIndex - lookAheadIndex这段可以进行填充，如果cas竞争（声明）成功，这一段数据都由当前生产者填充
            // 如果竞争失败，则退化为一个元素一个元素地填充

            if (lookAheadSeq == expectedLookAheadSeq && casProducerIndex(pIndex, expectedLookAheadSeq + 1))
            {
                for (int i = 0; i < lookAheadStep; i++)
                {
                    final long index = pIndex + i;
                    final long seqOffset = calcCircularLongElementOffset(index, mask);
                    final long offset = calcCircularRefElementOffset(index, mask);
                    // 必须等待Seq为期望值（消费者已完成所有操作） - 只有当seq为期望值时，可保证元素为null，且在seq上不会产生并发更新
                    while (lvLongElement(sBuffer, seqOffset) != index)
                    {

                    }

                    // 这里使用ordered模式存储，确保正确的构造和安全发布
                    // 注意Supplier对get方法的约束- 不可抛出元素，不可返回null，否则队列将永久处于破坏状态。
                    soRefElement(buffer, offset, s.get());
                    soLongElement(sBuffer, seqOffset, index + 1);
                }
                produced += lookAheadStep;
            }
            else
            {
                // 观望失败，lookAheadSeq < expectedLookAheadSeq 表示这段元素尚未完全被消费
                if (lookAheadSeq < expectedLookAheadSeq)
                {
                    // 判断当前索引是否可以进行填充，如果不能填充，则直接返回（因为接口对fill接口的表述为relaxedOffer）
                    if (notAvailable(pIndex, mask, sBuffer, pIndex))
                    {
                        return produced;
                    }
                }
                // 退化为一个一个地填充
                return produced + fillOneByOne(s, remaining);
            }
        }
        return limit;
    }

    /**
     * 判断指定所有的元素是否可用，说实话生产者和消费者都用该方法似乎不是个好主意。
     */
    private boolean notAvailable(long index, long mask, long[] sBuffer, long expectedSeq)
    {
        final long seqOffset = calcCircularLongElementOffset(index, mask);
        final long seq = lvLongElement(sBuffer, seqOffset);
        if (seq < expectedSeq)
        {
            return true;
        }
        // seq >= expectedSeq 似乎不是个好主意，实际上大于并不表示着可用的含义
        return false;
    }

    private int fillOneByOne(Supplier<E> s, int limit)
    {
        final long[] sBuffer = sequenceBuffer;
        final long mask = this.mask;
        final E[] buffer = this.buffer;

        // seq pIndex索引对应槽位的状态值（是否已填充，已消费）
        // seq == pIndex 表示该槽位应该被填充（此时竞争更新生产者索引）
        // seq < pIndex  表示该槽位尚未被消费，队列已满
        // seq > pIndex  表示该槽位已经被填充（初始为i, 填充之后 + 1）或已被消费（消费之后 + capacity）需要再下一环才能填充，此时需要重试

        long pIndex;
        long seqOffset;
        long seq;
        for (int i = 0; i < limit; i++)
        {
            do
            {
                pIndex = lvProducerIndex();
                seqOffset = calcCircularLongElementOffset(pIndex, mask);
                seq = lvLongElement(sBuffer, seqOffset);
                if (seq < pIndex)
                { // slot not cleared by consumer yet
                    // 表示该槽位尚未被消费，队列已满，此时直接返回，因为接口对fill的语义表述为relaxedOffer
                    return i;
                }
            }
            while (seq > pIndex || // another producer has moved the sequence
                !casProducerIndex(pIndex, pIndex + 1)); // failed to increment

            // 这里使用ordered模式存储，确保正确的构造和安全发布
            soRefElement(buffer, calcCircularRefElementOffset(pIndex, mask), s.get());
            soLongElement(sBuffer, seqOffset, pIndex + 1);
        }
        return limit;
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
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
