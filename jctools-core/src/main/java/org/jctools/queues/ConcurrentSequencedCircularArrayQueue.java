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

import static org.jctools.util.UnsafeLongArrayAccess.*;

abstract class ConcurrentSequencedCircularArrayQueue<E> extends ConcurrentCircularArrayQueue<E>
{
    /**
     * Q: 这个数组是干嘛的，和{@link #buffer}有什么区别？<br>
     * A:
     * 1. 正确性：在一个环形缓冲区中，如果是多消费者模型的队列，需要比空检查更强的保证，以防止重用{@link #buffer}时出现问题。
     * 对于生产者而言，不论是多生产者还是单生产者都可以根据元素是否为null进行下一步（是否填充）。
     * 但是对于多消费者模型下的消费者，却不能简单的根据元素不为null进行消费。
     * 为什么呢？因为速度快的消费者可能追上速度慢的消费者，如果没有额外措施，速度快的消费者将重复消费队列中的元素。
     * 因此我们需要额外的空间记录对应槽位的数据是否对应消费者索引，如果与消费者索引匹配，则表示表示可以消费，否则不能消费。
     * 2. 读性能：sequence可以减少生产者和消费者读取彼此的索引，从而避免大量的缓存行miss，从而提高读性能。
     */
    protected final long[] sequenceBuffer;

    public ConcurrentSequencedCircularArrayQueue(int capacity)
    {
        super(capacity);
        int actualCapacity = (int) (this.mask + 1);
        // pad data on either end with some empty slots. Note that actualCapacity is <= MAX_POW2_INT
        sequenceBuffer = allocateLongArray(actualCapacity);
        for (long i = 0; i < actualCapacity; i++)
        {
            // 细看，这里是初始化为i的，也就是说，如果producerIndex等于该槽位上的值，则应该填充
            soLongElement(sequenceBuffer, calcCircularLongElementOffset(i, mask), i);
        }
        // sequenceBuffer是final变量，因此具有初始化保证（安全发布保证）
    }
}
