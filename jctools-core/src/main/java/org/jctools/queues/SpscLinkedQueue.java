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

/**
 * 基于链表的单生产者单消费队列
 * <p>
 * 这是大佬D. Vyukov提出的基于链表(Node)的无锁单生产者单消费者(SPSC)队列，它是多生产者单消费者队列(MPSC)的弱化版本。
 * 原始版本已被适配为java版本，同时也实现了D. Vyukov提出的关于解决缓存行伪共享的内存布局：
 * <ol>
 * <li>通过继承可以确保生产者和消费者节点引用之间不会产生伪共享</li>
 * <li>由于是单生产者单消费者队列，因此（生产者）无需使用XCHG（交换指令），保证有序存储即可</li>
 * </ol>
 * 队列由一个存根节点（stub node）初始化，该节点同时设置为生产者和消费者节点引用。从这一点开始，遵循offer/poll的约定。
 *
 * This is a weakened version of the MPSC algorithm as presented
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>As this is an SPSC we have no need for XCHG, an ordered store is enough.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 *
 * @param <E>
 * @author nitsanw
 */
public class SpscLinkedQueue<E> extends BaseLinkedQueue<E>
{

    public SpscLinkedQueue()
    {
        LinkedQueueNode<E> node = newNode();
        spProducerNode(node);
        spConsumerNode(node);
        // Q: 怎么就保证了正确的构造呢？
        // A: 如果要使该对象对其它线程可见，必定要发布该对象，即接下来会产生一个Store指令。
        // 而storeOrderedNext会在next对应store指令[前]插入storeFence(LoadStore + StoreStore)，
        // 屏障保证next对应的store指令以及后续的其它store指令不会重排序到该屏障之前，也就保证了对象的发布不会重排序到对象的构造完成之前
        node.soNext(null); // this ensures correct construction: StoreStore
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 实现说明:<br>
     * Offer操作进允许单个线程调用。<br>
     * Offer分配一个新的node（持有给定的值）然后：
     * <ol>
     *     <li>用新的node置换当前producerNode</li>
     *     <li>将新的node链接到交换下来的旧的node后（保证从consumerNode最终可达）</li>
     * </ol>
     * 由此可见，producerNode.next始终为null，而对于所有其他节点，node.next不为null。
     * <p>
     *
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from a SINGLE thread.<br>
     * Offer allocates a new node (holding the offered value) and:
     * <ol>
     * <li>Sets the new node as the producerNode
     * <li>Sets that node as the lastProducerNode.next
     * </ol>
     * From this follows that producerNode.next is always null and for all other nodes node.next is not null.
     *
     * @see MessagePassingQueue#offer(Object)
     * @see java.util.Queue#offer(java.lang.Object)
     */
    @Override
    public boolean offer(final E e)
    {
        if (null == e)
        {
            throw new NullPointerException();
        }
        // 我提交bug之后，作者修改了此处实现，旧的实现先发布next，后发布producerNode，会导致consumerNode越过producerNode，从而破坏了约束条件。
        // https://github.com/JCTools/JCTools/issues/292
        // 先更新producerNode，再保证从consumerNode可达，这样才能保证consumerNode不会越过producerNode。

        final LinkedQueueNode<E> nextNode = newNode(e);
        LinkedQueueNode<E> oldNode = lpProducerNode();
        soProducerNode(nextNode);
        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next. This is a "bubble".
        // Inverting the order here will break the `isEmpty` invariant, and will require matching adjustments elsewhere.
        oldNode.soNext(nextNode);
        return true;
    }

    @Override
    public int fill(Supplier<E> s)
    {
        return MessagePassingQueueUtil.fillUnbounded(this, s);
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

        LinkedQueueNode<E> tail = newNode(s.get());
        final LinkedQueueNode<E> head = tail;
        for (int i = 1; i < limit; i++)
        {
            // 这里可以使用普通赋值模式，不必使用Ordered模式，可以节省开销
            // Q: 为什么可以使用普通赋值模式？
            // A: 这块代码其实相当于对象工厂，下面的oldPNode.soNext(head)可以保证消费者消费时，访问到正确构造的数据。
            // 因为消费者只有在next可见时才会消费数据。
            final LinkedQueueNode<E> temp = newNode(s.get());
            // spNext : soProducerNode ensures correct construction
            tail.spNext(temp);
            tail = temp;
        }
        final LinkedQueueNode<E> oldPNode = lpProducerNode();
        soProducerNode(tail);
        // same bubble as offer, and for the same reasons.
        oldPNode.soNext(head);
        return limit;
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }
}
