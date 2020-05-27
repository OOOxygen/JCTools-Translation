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

import org.jctools.util.Pow2;
import org.jctools.util.RangeUtil;

import static org.jctools.queues.LinkedArrayQueueUtil.length;
import static org.jctools.util.UnsafeRefArrayAccess.*;

/**
 * 一个容量从<i>initialCapacity</i>并以链接的块增长到<i>maxCapacity</i>的SPSC队列。
 * 每次都会将连接块容量加倍，直到底层的数组可以完全容纳所有的元素。
 * 仅当当前块(chunk)已满时才会扩容，未使用resize和拷贝元素的方法扩容，而是在旧块(当前块)存储一个到新块的链接。
 * 消费者可以通过该链接跟随生产者。
 *
 * An SPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks,
 * doubling theirs size every time until the full blown backing array is used.
 * The queue grows only when the current chunk is full and elements are not copied on
 * resize, instead a link to the new chunk is stored in the old chunk for the consumer to follow.<br>
 *
 * @param <E>
 */
public class SpscGrowableArrayQueue<E> extends BaseSpscLinkedArrayQueue<E>
{
    /**
     * 队列的最大容量
     */
    private final int maxQueueCapacity;
    /**
     * producerLimit的更新使用的观望步数（不太好直译）。
     * <p>
     * Q: 来了，来了，它又来了！这是个什么神奇的优化？
     * A: 生产者根据element是否为null判断是否可以填充该槽位，而不是判断{@code producerIndex}与{@code consumerIndex}的大小关系。
     * 在进行观望时，可以单步观望，也可以观望的远一点。这里假设了观望一段数据的性能好于单步观望，因此有了该设计。
     * <p>
     * Q: 为什么不使用capacity?
     * A: 观望步数越小，该设计的意义越小，越接近capacity就越容易失败，1/4可能是他们总结的一个经验值或理论值。
     * <p>
     * 可参考{@link SpscArrayQueue#lookAheadStep}理解。
     */
    private long lookAheadStep;

    public SpscGrowableArrayQueue(final int capacity)
    {
        this(Math.max(8, Pow2.roundToPowerOfTwo(capacity / 8)), capacity);
    }

    public SpscGrowableArrayQueue(final int chunkSize, final int capacity)
    {
        // Q: 为什么要保证 lookAheadStep最小为2？
        // A: 因为必须预留到下一个数组的指针槽位，因此只有当观望的结果大于等于2时，才能安全的插入元素，
        // 如果只观察到单个元素为null，那么本次观察结果毫无作用。

        RangeUtil.checkGreaterThanOrEqual(capacity, 16, "capacity");
        // minimal chunk size of eight makes sure minimal lookahead step is 2
        RangeUtil.checkGreaterThanOrEqual(chunkSize, 8, "chunkSize");

        maxQueueCapacity = Pow2.roundToPowerOfTwo(capacity);
        int chunkCapacity = Pow2.roundToPowerOfTwo(chunkSize);
        RangeUtil.checkLessThan(chunkCapacity, maxQueueCapacity, "chunkCapacity");

        long mask = chunkCapacity - 1;
        // need extra element to point at next array
        E[] buffer = allocateRefArray(chunkCapacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        adjustLookAheadStep(chunkCapacity);
    }

    @Override
    final boolean offerColdPath(
        final E[] buffer,
        final long mask,
        final long index,
        final long offset,
        final E v,
        final Supplier<? extends E> s)
    {
        final long lookAheadStep = this.lookAheadStep;
        // 一般情况下，进入下一环，或者当数组已满时调整大小（除非已到最大容量）
        // normal case, go around the buffer or resize if full (unless we hit max capacity)
        if (lookAheadStep > 0)
        {
            // 注意构造方法中保证了lookAheadStep最小为2

            long lookAheadElementOffset = calcCircularRefElementOffset(index + lookAheadStep, mask);
            // 尝试观望多个元素，这样我们就不必一直这样做（不必总是观望）
            // Try and look ahead a number of elements so we don't have to do this all the time
            if (null == lvRefElement(buffer, lookAheadElementOffset))
            {
                // 观望到当前数组尚有多个空间为null（大于等于3个槽），可以直接插入元素
                // Q: 为什么-1？
                // A: 因为必须预留一个槽位用于存储到下一个数组的指针，因此实际可用的槽位需要减1

                producerBufferLimit = index + lookAheadStep - 1; // joy, there's plenty of room
                writeToQueue(buffer, v == null ? s.get() : v, index, offset);
                return true;
            }

            // 走到这里表示观望失败，需要退化为单步检测。
            // 如果当前数组未达到容量上限，则在有两个槽的情况下才可以插入，如果只有一个槽，则用于存储JUMP，并进行扩容
            // 如果当前数组达到容量上限（不可以扩容），那么只要有空槽就可以插入。

            // we're at max capacity, can use up last element
            final int maxCapacity = maxQueueCapacity;
            if (mask + 1 == maxCapacity)
            {
                // 当前已是最大容量，那么不必查看是否还有额外的槽位（index + 1），槽位为空即插入
                // 因为不会继续扩容，不需要预留到下一个数组的指针（JUMP）
                if (null == lvRefElement(buffer, offset))
                {
                    writeToQueue(buffer, v == null ? s.get() : v, index, offset);
                    return true;
                }
                // 队列已满而且不可继续扩容，返回失败
                // we're full and can't grow
                return false;
            }

            // 当前数组尚未到达最大容量，因此必须为下个buffer的指针留出额外的空间（当前槽位用于存放JUMP标记）
            // 解释一下：当数组尚未到达最大容量时，数组必须有两个可用槽位时才能插入元素，如果只有一个槽位，则必须存储跳点

            // not at max capacity, so must allow extra slot for next buffer pointer
            if (null == lvRefElement(buffer, calcCircularRefElementOffset(index + 1, mask)))
            { // buffer is not full
                // 有两个槽可用，则当前尚可以继续插入，不必扩容
                writeToQueue(buffer, v == null ? s.get() : v, index, offset);
            }
            else
            {
                // 只有一个槽可用，则当前槽用于存储JUMP标记，并进行扩容
                // 这里注释有点问题，实际是2倍容量

                // allocate new buffer of same length
                final E[] newBuffer = allocateRefArray((int) (2 * (mask + 1) + 1));

                producerBuffer = newBuffer;
                producerMask = length(newBuffer) - 2;

                final long offsetInNew = calcCircularRefElementOffset(index, producerMask);
                linkOldToNew(index, buffer, offset, newBuffer, offsetInNew, v == null ? s.get() : v);
                int newCapacity = (int) (producerMask + 1);
                if (newCapacity == maxCapacity)
                {
                    // 数组到达最大容量，此时调整lookAheadStep为消费者到新数组的距离，其实就是缓存consumerIndex，避免两个数组的元素个数超过限制
                    // 为了区分这个过度状态，将其存为负值
                    long currConsumerIndex = lvConsumerIndex();
                    // use lookAheadStep to store the consumer distance from final buffer
                    this.lookAheadStep = -(index - currConsumerIndex);
                    producerBufferLimit = currConsumerIndex + maxCapacity;
                }
                else
                {
                    // 正常扩容节点
                    producerBufferLimit = index + producerMask - 1;
                    adjustLookAheadStep(newCapacity);
                }
            }
            return true;
        }
        // the step is negative (or zero) in the period between allocating the max sized buffer and the
        // consumer starting on it
        else
        {
            // 在生产者已创建最大大小的buffer和消费者尚未到达该buffer的过渡期间，lookAheadStep为负（或零）
            // Q: 这是要做什么？
            // A: 因为2倍扩容，因此如果当前数组达到最大大小，则两个数组存储的元素可能超过capacity限制，在这个过渡阶段必须检查size约束。
            // 之前不需要，是因为 1/2 + 1/4 + 1/8 +++ < 1，所有数组的元素之和一定不会超过capacity！！！

            final long prevElementsInOtherBuffers = -lookAheadStep;
            // until the consumer starts using the current buffer we need to check consumer index to
            // verify size
            long currConsumerIndex = lvConsumerIndex();
            int size = (int) (index - currConsumerIndex);
            int maxCapacity = (int) mask + 1; // we're on max capacity or we wouldn't be here
            if (size == maxCapacity)
            {
                // 消费者尚未到达该数组，队列已满，无法插入继续插入
                // consumer index has not changed since adjusting the lookAhead index, we're full
                return false;
            }
            // if consumerIndex progressed enough so that current size indicates it is on same buffer
            long firstIndexInCurrentBuffer = producerBufferLimit - maxCapacity + prevElementsInOtherBuffers;
            if (currConsumerIndex >= firstIndexInCurrentBuffer)
            {
                // 消费者进度已等于当前数组的起始索引，即消费者已过渡到当前数组，过渡期结束，调整lookAheadStep以退出当前分支
                // job done, we've now settled into our final state
                adjustLookAheadStep(maxCapacity);
            }
            // consumer is still on some other buffer
            else
            {
                // 消费者仍然在前面的某个数组上，需要继续等待
                // 由于firstIndexInCurrentBuffer计算依赖于producerBufferLimit，因此lookAheadStep也需要更新

                // how many elements out of buffer?
                this.lookAheadStep = (int) (currConsumerIndex - firstIndexInCurrentBuffer);
            }
            producerBufferLimit = currConsumerIndex + maxCapacity;
            writeToQueue(buffer, v == null ? s.get() : v, index, offset);
            return true;
        }
    }

    private void adjustLookAheadStep(int capacity)
    {
        lookAheadStep = Math.min(capacity / 4, SpscArrayQueue.MAX_LOOK_AHEAD_STEP);
    }

    @Override
    public int capacity()
    {
        return maxQueueCapacity;
    }
}
