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

/**
 * 在Fast Flow模型下，当数组元素可见/不可见时就被认为可消费/填充，而不会等待对方的索引更新，因此在某个线程内可能看见特殊的索引状态，比如：
 * <pre>
 * {@code consumerIndex > producerIndex}
 * {@code producerIndex - consumerIndex > capacity}
 * </pre>
 * 因此在计算size和isEmpty的时候，必须考虑索引问题。
 * <p>
 * 以上问题传送门：<br>
 * <a href="https://github.com/JCTools/JCTools/issues/292">poll null form non-empty queue</a><br>
 * <a href="https://github.com/JCTools/JCTools/issues/297">size may be greater than capacity</a><br>
 * <p>
 *
 * A note to maintainers on index assumptions: in a single threaded world it would seem intuitive to assume:
 * <pre>
 * <code>producerIndex >= consumerIndex</code>
 * </pre>
 * As an invariant, but in a concurrent, long running settings all of the following need to be considered:
 * <ul>
 *     <li> <code>consumerIndex > producerIndex</code> : due to counter overflow (unlikey with longs, but easy to reason)
 *     <li> <code>consumerIndex > producerIndex</code> : due to consumer FastFlow like implementation discovering the
 *     element before the counter is updated.
 *     <li> <code>producerIndex - consumerIndex < 0</code> : due to above.
 *     <li> <code>producerIndex - consumerIndex > Integer.MAX_VALUE</code> : as linked buffers allow constructing queues
 *     with more than <code>Integer.MAX_VALUE</code> elements.
 *
 * </ul>
 */
@InternalAPI
public final class IndexedQueueSizeUtil
{
    public static int size(IndexedQueue iq)
    {
        /*
         * It is possible for a thread to be interrupted or reschedule between the read of the producer and
         * consumer indices, therefore protection is required to ensure size is within valid range. In the
         * event of concurrent polls/offers to this method the size is OVER estimated as we read consumer
         * index BEFORE the producer index.
         */
        long after = iq.lvConsumerIndex();
        long size;
        while (true)
        {
            final long before = after;
            final long currentProducerIndex = iq.lvProducerIndex();
            after = iq.lvConsumerIndex();
            if (before == after)
            {
                size = (currentProducerIndex - after);
                break;
            }
        }
        // Long overflow is impossible (), so size is always positive. Integer overflow is possible for the unbounded
        // indexed queues.
        if (size > Integer.MAX_VALUE)
        {
            return Integer.MAX_VALUE;
        }
        // （类文档）并发的更新cIndex和pIndex可能导致其中一个进度落后与另一个进度（如：FastFlow模型），因此我们需要检查边界。
        // concurrent updates to cIndex and pIndex may lag behind other progress enablers (e.g. FastFlow), so we need
        // to check bounds
        else if (size < 0)
        {
            return 0;
        }
        else if (iq.capacity() != MessagePassingQueue.UNBOUNDED_CAPACITY && size > iq.capacity())
        {
            return iq.capacity();
        }
        else
        {
            return (int) size;
        }
    }

    public static boolean isEmpty(IndexedQueue iq)
    {
        // 顺序很重要！
        // 先读取consumerIndex再读取producerIndex，允许了生产者在加载consumerIndex之后增加procuderIndex，
        // 这样可以保证该方法的估算值是保守的。
        // 注意，对于MPMC，我们无法做任何事情来使其成为精确的方法。

        // Order matters!
        // Loading consumer before producer allows for producer increments after consumer index is read.
        // This ensures this method is conservative in it's estimate. Note that as this is an MPMC there is
        // nothing we can do to make this an exact method.
        return (iq.lvConsumerIndex() >= iq.lvProducerIndex());
    }

    @InternalAPI
    public interface IndexedQueue
    {
        long lvConsumerIndex();

        long lvProducerIndex();

        int capacity();
    }
}
