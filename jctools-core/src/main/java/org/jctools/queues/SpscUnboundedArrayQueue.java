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

import static org.jctools.util.UnsafeRefArrayAccess.*;

/**
 * 一个容量从<i>initialCapacity</i>开始并以初始大小的链接块无限期增长的SPSC队列。
 * 仅当当前块(chunk)已满时才会扩容，未使用resize和拷贝元素的方法扩容，而是在旧块(当前块)存储一个到新块的链接。
 * 消费者可以通过该链接跟随生产者。
 *
 * An SPSC array queue which starts at <i>initialCapacity</i> and grows indefinitely in linked chunks of the initial size.
 * The queue grows only when the current chunk is full and elements are not copied on
 * resize, instead a link to the new chunk is stored in the old chunk for the consumer to follow.<br>
 *
 * @param <E>
 */
public class SpscUnboundedArrayQueue<E> extends BaseSpscLinkedArrayQueue<E>
{

    /**
     * @param chunkSize 每个块的有效内容大小
     */
    public SpscUnboundedArrayQueue(int chunkSize)
    {
        int chunkCapacity = Math.max(Pow2.roundToPowerOfTwo(chunkSize), 16);
        long mask = chunkCapacity - 1;
        // 额外的1个插槽用于存储到下一个数组的指针（引用）
        E[] buffer = allocateRefArray(chunkCapacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        producerBufferLimit = mask - 1; // we know it's all empty to start with

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

        // 到这里，表示可能进入下一环或需要添加新的buffer
        // go around the buffer or add a new buffer
        if (null == lvRefElement(buffer, calcCircularRefElementOffset(pBufferLimit, mask)))
        {
            // 观望到当前数组接下来lookAheadStep个元素都为null，观望成功，不必扩容
            producerBufferLimit = pBufferLimit - 1; // joy, there's plenty of room
            writeToQueue(buffer, v == null ? s.get() : v, pIndex, offset);
        }
        // 观望失败，退化为单步检查
        else if (null == lvRefElement(buffer, calcCircularRefElementOffset(pIndex + 1, mask)))
        { // buffer is not full
            // 下一个元素为null，表面当前数组未满，可以直接插入元素
            writeToQueue(buffer, v == null ? s.get() : v, pIndex, offset);
        }
        else
        {
            // 下一个元素不为null，证明队列已满，需要触发扩容，需要分配一个新数组，并将引用存储到当前buffer的末尾
            // 数组的最后一个插槽是额外分配的，是用于存储到下一个数组的指针的，因此数组并不是真正的已满
            // 注意这里分配的数组长度：它总是和当前数组的长度相同，mask + 2 其实就是 buffer.length

            // we got one slot left to write into, and we are not full. Need to link new buffer.
            // allocate new buffer of same length
            final E[] newBuffer = allocateRefArray((int) (mask + 2));
            producerBuffer = newBuffer;
            producerBufferLimit = pIndex + mask - 1;

            linkOldToNew(pIndex, buffer, offset, newBuffer, offset, v == null ? s.get() : v);
        }
        return true;
    }

    @Override
    public int fill(Supplier<E> s)
    {
        return fill(s, (int) this.producerMask);
    }

    @Override
    public int capacity()
    {
        return UNBOUNDED_CAPACITY;
    }
}
