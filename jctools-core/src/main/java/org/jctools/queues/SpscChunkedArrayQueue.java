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

import static org.jctools.util.UnsafeRefArrayAccess.*;

/**
 * 一个容量从<i>initialCapacity</i>并以初始大小的链接块增长到<i>maxCapacity</i>的SPSC队列。
 * 仅当当前块(chunk)已满时才会扩容，未使用resize和拷贝元素的方法扩容，而是在旧块(当前块)存储一个到新块的链接。
 * 消费者可以通过该链接跟随生产者。
 *
 * An SPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks
 * of the initial size. The queue grows only when the current chunk is full and elements are not copied on
 * resize, instead a link to the new chunk is stored in the old chunk for the consumer to follow.<br>
 *
 * @param <E>
 */
public class SpscChunkedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E>
{
    /**
     * 队列最大容量 - 会影响扩容
     */
    private final int maxQueueCapacity;
    /**
     * 生产者在队列概念上第一个不可使用的生产者索引
     */
    private long producerQueueLimit;

    public SpscChunkedArrayQueue(int capacity)
    {
        this(Math.max(8, Pow2.roundToPowerOfTwo(capacity / 8)), capacity);
    }

    public SpscChunkedArrayQueue(int chunkSize, int capacity)
    {
        RangeUtil.checkGreaterThanOrEqual(capacity, 16, "capacity");
        // minimal chunk size of eight makes sure minimal lookahead step is 2
        RangeUtil.checkGreaterThanOrEqual(chunkSize, 8, "chunkSize");

        maxQueueCapacity = Pow2.roundToPowerOfTwo(capacity);
        int chunkCapacity = Pow2.roundToPowerOfTwo(chunkSize);
        RangeUtil.checkLessThan(chunkCapacity, maxQueueCapacity, "chunkCapacity");

        long mask = chunkCapacity - 1;
        // 额外的一个空间用于存储到下一个数组的指针（引用）
        // need extra element to point at next array
        E[] buffer = allocateRefArray(chunkCapacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with
        producerQueueLimit = maxQueueCapacity;

        // 这里并未考虑正确的构造（安全发布）一事，理论上将并不安全
    }

    @Override
    final boolean offerColdPath(E[] buffer, long mask, long pIndex, long offset, E v, Supplier<? extends E> s)
    {
        // 因为每个块(chunk)的大小一致，因此采用固定算法计算lookAheadStep - 即总是一个固定值（总是有效容量的1/4）
        // 解释一下，观望步数越小，该设计的意义越小，越接近capacity就越容易失败，1/4可能是他们总结的一个经验值或理论值。

        // use a fixed lookahead step based on buffer capacity
        final long lookAheadStep = (mask + 1) / 4;
        long pBufferLimit = pIndex + lookAheadStep;

        long pQueueLimit = producerQueueLimit;

        if (pIndex >= pQueueLimit)
        {
            // 生产者索引超过了缓存的队列上索引限制，需要刷新缓存，判断队列是否真的已满（达到capacity限制）
            // 注意：读取的始终是滞后的消费者索引，因此一定不会超过最大容量。

            // we tested against a potentially out of date queue limit, refresh it
            final long cIndex = lvConsumerIndex();
            producerQueueLimit = pQueueLimit = cIndex + maxQueueCapacity;
            // if we're full we're full
            if (pIndex >= pQueueLimit)
            {
                // 队列确实已满（到达容量上限）
                return false;
            }
        }

        // 走到这里，证明队列尚未到达容量上限，给定元素一定可以插入，可能扩容。

        // 如果在当前buffer上的limit大于队列级别的限制，那么当前buffer上的限制需要遵从队列级别的限制，由于要处理溢出情况，因此不简单的使用Math.min
        // Q: 这是个什么意思？
        // A: 数组还有额外空间，但是用户设定了最大容量，即使数组尚有额外空间，也不允许插入。

        // if buffer limit is after queue limit we use queue limit. We need to handle overflow so
        // cannot use Math.min
        if (pBufferLimit - pQueueLimit > 0)
        {
            pBufferLimit = pQueueLimit;
        }

        // 到这里，表示可能进入下一环或需要添加新的buffer
        // go around the buffer or add a new buffer
        if (pBufferLimit > pIndex + 1 && // there's sufficient room in buffer/queue to use pBufferLimit
            null == lvRefElement(buffer, calcCircularRefElementOffset(pBufferLimit, mask)))
        {
            // 观望到当前数组尚有多个空间为null（大于等于3个槽），可以直接插入元素
            // Q: 为什么-1？
            // A: 因为必须预留一个槽位用于存储到下一个数组的指针，因此实际可用的槽位需要减1

            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, v == null ? s.get() : v, pIndex, offset);
        }
        // 观望多个失败，退化为单步检查，
        else if (null == lvRefElement(buffer, calcCircularRefElementOffset(pIndex + 1, mask)))
        { // buffer is not full
            // 有两个槽可用，则当前尚可以继续插入，不必扩容
            writeToQueue(buffer, v == null ? s.get() : v, pIndex, offset);
        }
        else
        {
            // 只有一个槽可用，则当前槽用于存储JUMP标记，并进行扩容
            // 数组的最后一个插槽是额外分配的，是用于存储到下一个数组的指针的，因此数组并不是真正的已满
            // 注意这里分配的数组长度：它总是和当前数组的长度相同，mask + 2 其实就是 buffer.length

            // we got one slot left to write into, and we are not full. Need to link new buffer.
            // allocate new buffer of same length
            final E[] newBuffer = allocateRefArray((int) (mask + 2));
            producerBuffer = newBuffer;

            linkOldToNew(pIndex, buffer, offset, newBuffer, offset, v == null ? s.get() : v);
        }
        return true;
    }

    @Override
    public int capacity()
    {
        return maxQueueCapacity;
    }
}
