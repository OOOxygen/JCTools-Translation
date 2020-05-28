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


/**
 * An MPSC array queue which starts at <i>initialCapacity</i> and grows to <i>maxCapacity</i> in linked chunks,
 * doubling theirs size every time until the full blown backing array is used.
 * The queue grows only when the current chunk is full and elements are not copied on
 * resize, instead a link to the new chunk is stored in the old chunk for the consumer to follow.
 */
public class MpscGrowableArrayQueue<E> extends MpscChunkedArrayQueue<E>
{

    public MpscGrowableArrayQueue(int maxCapacity)
    {
        super(Math.max(2, Pow2.roundToPowerOfTwo(maxCapacity / 8)), maxCapacity);
    }

    /**
     * @param initialCapacity the queue initial capacity. If chunk size is fixed this will be the chunk size.
     *                        Must be 2 or more.
     * @param maxCapacity     the maximum capacity will be rounded up to the closest power of 2 and will be the
     *                        upper limit of number of elements in this queue. Must be 4 or more and round up to a larger
     *                        power of 2 than initialCapacity.
     */
    public MpscGrowableArrayQueue(int initialCapacity, int maxCapacity)
    {
        super(initialCapacity, maxCapacity);
    }


    @Override
    protected int getNextBufferSize(E[] buffer)
    {
        // 最大容量检查
        final long maxSize = maxQueueCapacity / 2;
        RangeUtil.checkLessThanOrEqual(length(buffer), maxSize, "buffer.length");
        // 注意这里的扩容策略：这是与其他实现不同的地方，这里是2倍扩容，其它地方是创建相同大小的数组
        final int newSize = 2 * (length(buffer) - 1);
        return newSize + 1;
    }

    @Override
    protected long getCurrentBufferCapacity(long mask)
    {
        // 当数组到达最大容量后，因为不会再扩容，因此不再为JUMP预留空间，因此有效容量就是maxCapacity
        // 在未到达最大容量时，需要为JUMP预留空间，因此有效容量需要会少1个(也就是位移后的mask)
        return (mask + 2 == maxQueueCapacity) ? maxQueueCapacity : mask;
    }
}
