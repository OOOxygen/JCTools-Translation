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
     * A: 用于描述{@link #buffer}每个槽位的状态（是否已被填充或消费）。<br>
     * <p>
     * Q: 有生产者索引和消费者索引的情况下为何还要这个？
     * A: 避免生产者和消费者读取彼此的索引，提高读性能 - 读取彼此的索引容易造成缓存行miss。
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
