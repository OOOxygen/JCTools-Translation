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

import org.jctools.util.InternalAPI;

import java.util.Arrays;

import static org.jctools.util.UnsafeLongArrayAccess.*;

/**
 * 如果说每一个Chunk其实都是一个{@link ConcurrentCircularArrayQueue} + {@link LinkedQueueNode}的组合，
 * 那么每一个{@link MpmcUnboundedXaddChunk}都是一个{@link ConcurrentSequencedCircularArrayQueue} + {@link LinkedQueueNode}的组合。
 */
@InternalAPI
final class MpmcUnboundedXaddChunk<E> extends MpUnboundedXaddChunk<MpmcUnboundedXaddChunk<E>, E>
{
    /**
     * Q: 这个sequence是什么？是不是和{@link ConcurrentSequencedCircularArrayQueue#sequenceBuffer}一样？
     * A: 不一样，这里的sequence存储的是chunkIndex，即每个槽位的元素是否属于该chunk。
     * 对于生产者而言，只要槽位对于的元素为null，就可以填充，填充后将对于的sequence设置为index。
     * 对于消费者而言，只有当sequence等于index时才可以消费，确保该槽位的数据对应于消费者的索引。
     * <p>
     * 注意：只有缓冲池中的chunk是通过sequence交互的，注意看构造方法。
     * <p>
     * Q: 那为什么只有缓冲池中的chunk使用sequence呢？
     * A: 如果不是缓冲池中的chunk，根据element是否为null即可做出判断，而如果是缓冲池中的chunk，由于消费者可能提前归还chunk，
     * 因此又回归到{@link MpmcArrayQueue}的解决方案，通过sequence保证该槽位的数据对应于消费者的索引。
     * <p>
     * 这里生产者在填充元素之后，sequence中存储的是chunk的index，这个和disruptor的多生产者模式的availableBuffer就很像了（标记这个数据属于第几环）。
     */
    private final long[] sequence;

    MpmcUnboundedXaddChunk(long index, MpmcUnboundedXaddChunk<E> prev, int size, boolean pooled)
    {
        super(index, prev, size, pooled);
        if (pooled)
        {
            sequence = allocateLongArray(size);
            Arrays.fill(sequence, MpmcUnboundedXaddChunk.NOT_USED);
        }
        else
        {
            sequence = null;
        }
    }

    void soSequence(int index, long e)
    {
        assert isPooled();
        soLongElement(sequence, calcLongElementOffset(index), e);
    }

    long lvSequence(int index)
    {
        assert isPooled();
        return lvLongElement(sequence, calcLongElementOffset(index));
    }

    void spinForSequence(int index, long e)
    {
        assert isPooled();
        final long[] sequence = this.sequence;
        final long offset = calcLongElementOffset(index);
        while (true)
        {
            if (lvLongElement(sequence, offset) == e)
            {
                break;
            }
        }
    }
}
