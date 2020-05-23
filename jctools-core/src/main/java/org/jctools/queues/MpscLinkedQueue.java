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

import org.jctools.util.UnsafeAccess;

import java.util.Queue;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * 这是大佬D. Vyukov提出的基于链表(Node)的无锁多生产者单消费者(MPSC)队列。
 * 原始版本已被适配为java版本，同时也实现了D. Vyukov提出的关于解决缓存行伪共享的内存布局：
 * <ol>
 * <li>通过继承可以确保生产者和消费者节点引用之间不会产生伪共享</li>
 * <li>尽最大可能使用JDK提供的XCHG（交换指令）功能（请参阅JDK7 / 8实现中的差异 - Unsafe类）</li>
 * <li>poll方法符合{@link java.util.Queue}接口的约定，原始语义可通过relaxedPoll获得。</li>
 * </ol>
 * 队列由一个存根节点（stub node）初始化，该节点同时设置为生产者和消费者节点引用。从这一点开始，遵循offer/poll的约定。
 *
 * This is a Java port of the MPSC algorithm as presented
 * <a href="http://www.1024cores.net/home/lock-free-algorithms/queues/non-intrusive-mpsc-node-based-queue"> on
 * 1024 Cores</a> by D. Vyukov. The original has been adapted to Java and it's quirks with regards to memory
 * model and layout:
 * <ol>
 * <li>Use inheritance to ensure no false sharing occurs between producer/consumer node reference fields.
 * <li>Use XCHG functionality to the best of the JDK ability (see differences in JDK7/8 impls).
 * <li>Conform to {@link java.util.Queue} contract on poll. The original semantics are available via relaxedPoll.
 * </ol>
 * The queue is initialized with a stub node which is set to both the producer and consumer node references.
 * From this point follow the notes on offer/poll.
 */
public class MpscLinkedQueue<E> extends BaseLinkedQueue<E>
{

    /**
     * Q: 这里为什么调用{@link #xchgProducerNode(LinkedQueueNode)}，直接赋值不行吗？
     * A: 其实与{@link SpscLinkedQueue}构造时调用{@link LinkedQueueNode#soNext(LinkedQueueNode)}有异曲同工之妙。
     * 用于保证对象的正确构造，避免构造方法之后的对象发布操作重排序到构造方法返回之前。
     * 但是两个类使用了不同的方式保证安全发布，增加了理解难度。
     */
    public MpscLinkedQueue()
    {
        LinkedQueueNode<E> node = newNode();
        spConsumerNode(node);
        xchgProducerNode(node);
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 生产者们应该先发布新的producerNode(tail)，再保证从consumerNode(head)可达。
     * 这里的实现是正确的，而{@link SpscLinkedQueue}的实现则是错误的（v3.0.0）。
     * <p>
     * 实现说明：<br>
     * Offer 方法允许多个线程同时调用。<br>
     * Offer 方法先分配一个新的node，然后：
     * <ol>
     * <li>与当前的producerNode进行原子交换(只有一个生产者会胜出)</li>
     * <li>将新的node链接到交换下来的旧的node后（保证从consumerNode最终可达）</li>
     * </ol>
     * 之所以可行，是因为每个生产者都可以保证“插入”新节点并链接旧节点。作为XCHG（交换指令）保证的一部分，没有2个生产者可以得到相同的生产者节点。
     *
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Offer is allowed from multiple threads.<br>
     * Offer allocates a new node and:
     * <ol>
     * <li>Swaps it atomically with current producer node (only one producer 'wins')
     * <li>Sets the new node as the node following from the swapped producer node
     * </ol>
     * This works because each producer is guaranteed to 'plant' a new node and link the old node. No 2
     * producers can get the same producer node as part of XCHG guarantee.
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
        final LinkedQueueNode<E> nextNode = newNode(e);
        // 原子交换，此时已发布nextNode为producerNode，已经对其它线程可见，但尚不可达
        final LinkedQueueNode<E> prevProducerNode = xchgProducerNode(nextNode);

        // 进行链接，当链接完成前，其它线程会看见链表处于断开状态，一旦链接完成，最终从consumerNode可达
        // 如果生产者线程在此中断（让出CPU），则链将断开（一段时间），直到该线程恢复并完成存储prev.next。这是一个“泡沫”。

        // Should a producer thread get interrupted here the chain WILL be broken until that thread is resumed
        // and completes the store in prev.next. This is a "bubble".
        prevProducerNode.soNext(nextNode);
        return true;
    }

    /**
     * {@inheritDoc}
     * <p>
     * 仅当从(单个)消费者线程调用该方法是安全的，并在与生产者产生竞争时尽最大努力。当队列里存在“泡沫”时，该方法有潜在的阻塞可能。<br>
     * “bubble”在{@link #offer(Object)}中提到，它指的是当前的生产者节点尚无法从consumerNode可达，但是最终一定可达，这期间需要等待。
     *
     * <p>
     * This method is only safe to call from the (single) consumer thread, and is subject to best effort when racing
     * with producers. This method is potentially blocking when "bubble"s in the queue are visible.
     */
    @Override
    public boolean remove(Object o)
    {
        if (null == o)
        {
            return false; // Null elements are not permitted, so null will never be removed.
        }

        final LinkedQueueNode<E> originalConsumerNode = lpConsumerNode();
        // originalConsumerNode已消费的节点，是队列的head，是哨兵，因此是preConsumerNode，它的下一个才是我们当前要消费的节点
        LinkedQueueNode<E> prevConsumerNode = originalConsumerNode;
        // getNextConsumerNode这个方法可能会阻塞,会等待当前尚不可达的节点可达
        LinkedQueueNode<E> currConsumerNode = getNextConsumerNode(originalConsumerNode);
        while (currConsumerNode != null)
        {
            if (o.equals(currConsumerNode.lpValue()))
            {
                // 我们需要将nextNode链接到preConsumerNode上，从而删除当前节点（这应该不难理解）
                LinkedQueueNode<E> nextNode = getNextConsumerNode(currConsumerNode);
                // e.g.: consumerNode -> node0 -> node1(o==v) -> node2 ... => consumerNode -> node0 -> node2
                if (nextNode != null)
                {
                    // 这里不为null，意味着删除的是一个中间节点,可以简单的完成链接，执行结束
                    // We are removing an interior node.
                    prevConsumerNode.soNext(nextNode);

                }
                // This case reflects: prevConsumerNode != originalConsumerNode && nextNode == null
                // At rest, this would be the producerNode, but we must contend with racing. Changes to subclassed
                // queues need to consider remove() when implementing offer().
                else
                {
                    // 警告:原始注释有问题，假设队列只有一个元素，就是要删除的那个元素，那么走到这里的时候 prevConsumerNode == originalConsumerNode是成立的。
                    // 走到这的时候，只能证明当前线程认为currConsumerNode是当前生产者节点，即到达了队列的尾部，删除操作需要更新producerNode，即会和生产者产生竞争。
                    // 此时，需要尝试原子方式(CAS)将其更新为上一个节点(prevConsumerNode) (在此之前需要将next引用置为null，必须满足生产者节点next始终为null的约束，否则将出现bug)，
                    // 如果成功，则证明期间没有生产者插入数据，那么操作结束。
                    // 如果失败，则证明期间有生产者插入了数据，那么只需要等待下一个消费者节点可见(插入数据的那个生产者完成链接), 然后链接即可.

                    // producerNode is currConsumerNode, try to atomically update the reference to move it to the
                    // previous node.
                    prevConsumerNode.soNext(null);
                    if (!casProducerNode(currConsumerNode, prevConsumerNode))
                    {
                        // If the producer(s) have offered more items we need to remove the currConsumerNode link.
                        nextNode = spinWaitForNextNode(currConsumerNode);
                        prevConsumerNode.soNext(nextNode);
                    }
                }

                // 避免GC裙带关系，因为我们将丢弃该节点 - 清理引用，避免影响next和value引用的对象的生命周期， help gc
                // Avoid GC nepotism because we are discarding the current node.
                currConsumerNode.soNext(null);
                currConsumerNode.spValue(null);

                return true;
            }
            prevConsumerNode = currConsumerNode;
            currConsumerNode = getNextConsumerNode(currConsumerNode);
        }
        return false;
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

        // 和SpscLinkedQueue相同的方案
        // 由于是链表，因此可以简单将多次写操作合并为一次写操作
        // 先构建Limit数量的节点，并将其构建为一个链表，然后用tail交换producerNode，最后将head链接到交换下来的旧producerNode上即可
        LinkedQueueNode<E> tail = newNode(s.get());
        final LinkedQueueNode<E> head = tail;
        for (int i = 1; i < limit; i++)
        {
            // 这里可以使用普通赋值模式，不必使用Ordered模式，可以节省开销
            // Q: 为什么可以使用普通赋值模式？
            // A: 这块代码其实相当于对象工厂，下面的原子交换指令可以为这里提供保护。
            final LinkedQueueNode<E> temp = newNode(s.get());
            tail.soNext(temp);
            tail = temp;
        }
        final LinkedQueueNode<E> oldPNode = xchgProducerNode(tail);
        oldPNode.soNext(head);
        return limit;
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }

    /**
     * 交换生产者节点，即将新节点赋值为{@code producerNode}并返回之前的值。
     * PS: XCHG为交换指令
     */
    // $gen:ignore
    private LinkedQueueNode<E> xchgProducerNode(LinkedQueueNode<E> newVal)
    {
        if (UnsafeAccess.SUPPORTS_GET_AND_SET_REF)
        {
            return (LinkedQueueNode<E>) UNSAFE.getAndSetObject(this, P_NODE_OFFSET, newVal);
        }
        else
        {
            LinkedQueueNode<E> oldVal;
            do
            {
                oldVal = lvProducerNode();
            }
            while (!UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, oldVal, newVal));
            return oldVal;
        }
    }

    /**
     * 获取给定消费者节点的下一个节点，它会在遇见“泡沫”节点时自旋等待。
     * 泡沫节点 - 当前尚不可达节点.
     * <p>
     * 其实{@link #poll()} {@link #peek()}都可以使用该方法以避免重复实现(我还得注释多遍...).
     * 这个方法的名字也有点...不能表达意图
     */
    private LinkedQueueNode<E> getNextConsumerNode(LinkedQueueNode<E> currConsumerNode)
    {
        LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode == null && currConsumerNode != lvProducerNode())
        {
            // nextNode == null 意味着下一个节点尚不可达
            // currConsumerNode != lvProducerNode() 意味着下一个节点存在
            // 即:下一个节点存在但尚不可达时，我们知道最终一定可达，因此我们自旋等待，直到生产者完成链接。
            nextNode = spinWaitForNextNode(currConsumerNode);
        }
        return nextNode;
    }

}
