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
package io.jaq.mpsc;

import static io.jaq.util.Pow2.findNextPositivePowerOfTwo;
import static io.jaq.util.Pow2.isPowerOf2;
import io.jaq.ConcurrentQueue;
import io.jaq.ConcurrentQueueConsumer;
import io.jaq.ConcurrentQueueProducer;

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Use a set number of parallel MPSC queues to diffuse the contention on tail.
 */
abstract class MpscCompoundQueueL0Pad<E> extends AbstractQueue<E>{
    long p00, p01, p02, p03, p04, p05, p06;
    long p30, p31, p32, p33, p34, p35, p36, p37;
}

abstract class MpscCompoundQueueColdFields<E> extends MpscCompoundQueueL0Pad<E> {
    // must be power of 2
    protected final int parallelQueues;
    protected final int parallelQueuesMask;
    protected final MpscConcurrentQueue<E>[] queues;

    @SuppressWarnings("unchecked")
    public MpscCompoundQueueColdFields(int capacity, int queueParallelism) {
        parallelQueues = isPowerOf2(queueParallelism) ? queueParallelism
                : findNextPositivePowerOfTwo(queueParallelism) / 2;
        parallelQueuesMask = parallelQueues - 1;
        queues = new MpscConcurrentQueue[parallelQueues];
        for (int i = 0; i < parallelQueues; i++) {
            queues[i] = new MpscConcurrentQueue<E>(findNextPositivePowerOfTwo(capacity) / parallelQueues);
        }
    }
}

abstract class MpscCompoundQueueMidPad<E> extends MpscCompoundQueueColdFields<E> {
    long p20, p21, p22, p23, p24, p25, p26;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscCompoundQueueMidPad(int capacity, int queueParallelism) {
        super(capacity, queueParallelism);
    }
}
abstract class MpscCompoundQueueConsumerQueueIndex<E> extends MpscCompoundQueueMidPad<E> {
    int consumerQueueIndex;

    public MpscCompoundQueueConsumerQueueIndex(int capacity, int queueParallelism) {
        super(capacity, queueParallelism);
    }
}

public final class MpscCompoundQueue<E> extends MpscCompoundQueueConsumerQueueIndex<E> implements
        ConcurrentQueue<E>, ConcurrentQueueConsumer<E>, ConcurrentQueueProducer<E> {
    private static final int CPUS = Runtime.getRuntime().availableProcessors();
    long p00, p01, p02, p03, p04, p05, p06;
    long p30, p31, p32, p33, p34, p35, p36, p37;

    public MpscCompoundQueue(int capacity) {
        super(capacity, CPUS);
    }

    @Override
    public boolean offer(final E e) {
        int start = (int) (Thread.currentThread().getId() & parallelQueuesMask);
        if (queues[start].offer(e)) {
            return true;
        } else {
            for (;;) {
                int status = 0;
                for (int i = start; i < start + parallelQueues; i++) {
                    int s = queues[i & parallelQueuesMask].tryOffer(e);
                    if (s == 0) {
                        return true;
                    }
                    status += s;
                }
                if (status == parallelQueues) {
                    return false;
                }
            }
        }
    }

    @Override
    public E poll() {
        int qIndex = consumerQueueIndex & parallelQueuesMask;
        int limit = qIndex + parallelQueues;
        E e = null;
        for (; qIndex < limit; qIndex++) {
            e = queues[qIndex & parallelQueuesMask].poll();
            if (e != null) {
                break;
            }
        }
        consumerQueueIndex = qIndex;
        return e;
    }
    
    @Override
    public E peek() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        int size = 0;
        for (MpscConcurrentQueue<E> lane : queues) {
            size += lane.size();
        }
        return size;
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ConcurrentQueueConsumer<E> consumer() {
        return this;
    }

    @Override
    public ConcurrentQueueProducer<E> producer() {
        return this;
    }

    @Override
    public int capacity() {
        return parallelQueues * queues[0].capacity();
    }
}
