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

import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.Queue;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

/**
 * 简单解释下缓存行填充：通过填充无效字段，可以使得不使用我们数据的线程不会因为操作其它数据而锁定我们使用的缓存行，从而拥有更好的性能。
 * 关于缓存行填充的更多资料请谷歌。
 * <p>
 * PS: 缓存行分64(8字宽)字节和128字节(16字宽)版本，在JCTools的缓存行填充中，填充的都是128个字节，这样兼容64字节的128字节的缓存行。
 */
abstract class BaseLinkedQueuePad0<E> extends AbstractQueue<E> implements MessagePassingQueue<E>
{
    /**
     * 缓存行填充，避免{@code producerNode}产生伪共享。
     * <p>
     * 在JDK1.8中，添加了{@code Contended}注解，可以实现自动的缓存行填充，在{@link java.util.concurrent.ConcurrentHashMap}中有运用。
     * 在JDK11中又放到了internal...
     * <p>
     * 有效字段为120字节，作者利用了对象头充当剩余的填充（对象头信息超过8字节）。
     * <p>
     * 之前是使用long进行填充，最新版这个填充方式有点....
     */
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    // byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
    //    * drop 8b as object header acts as padding and is >= 8b *
}

// $gen:ordered-fields
abstract class BaseLinkedQueueProducerNodeRef<E> extends BaseLinkedQueuePad0<E>
{
    final static long P_NODE_OFFSET = fieldOffset(BaseLinkedQueueProducerNodeRef.class, "producerNode");

    /**
     * 生产者当前发布的数据节点 - 队列的尾节点。
     * <p>
     * 目前的约束是：
     * 1. 该属性始终不为null
     * 2. next属性一直为null
     * <p>
     * 在基于链表的队列下，生产者需要先更新该节点，再使其从{@code consumerNode}可达。
     * （因为在目前的的Node设计中，Node之间是没有‘大小/先后’关系的，如果Node存储producerIndex，那么实现和Fast Flow相同的效果）
     */
    private volatile LinkedQueueNode<E> producerNode;

    /**
     * storePlainProducerNode
     * 该方法无法保证安全发布，因此在后续的修改中，除构造方法外，都使用了{@link #soProducerNode(LinkedQueueNode)}
     */
    final void spProducerNode(LinkedQueueNode<E> newValue)
    {
        UNSAFE.putObject(this, P_NODE_OFFSET, newValue);
    }

    /**
     * storeOrderedProducerNode
     * 可以实现安全发布，确保newValue构造完成，以避免非消费者访问节点时看见未构造完成的对象。
     */
    final void soProducerNode(LinkedQueueNode<E> newValue)
    {
        UNSAFE.putOrderedObject(this, P_NODE_OFFSET, newValue);
    }

    /**
     * loadVolatileProducerNode
     * 多生产者模式下，必须使用该方法读取。
     * 单生产者模式下，当不确定是生产者线程时使用该方法读取。
     */
    final LinkedQueueNode<E> lvProducerNode()
    {
        return producerNode;
    }

    /**
     * 多生产者模式下，消费者删除元素与生产者填充元素之间产生竞争时需要使用该方法。
     * (生产者之间竞争使用的是交换指令，并未放在这里)
     */
    final boolean casProducerNode(LinkedQueueNode<E> expect, LinkedQueueNode<E> newValue)
    {
        return UNSAFE.compareAndSwapObject(this, P_NODE_OFFSET, expect, newValue);
    }

    /**
     * loadPlainProducerNode
     * 当为单生产者模式时，生产者使用该方法读取即可，因为该值只有生产者更新该值。
     */
    final LinkedQueueNode<E> lpProducerNode()
    {
        return producerNode;
    }
}

abstract class BaseLinkedQueuePad1<E> extends BaseLinkedQueueProducerNodeRef<E>
{
    /**
     * 缓存行填充，避免{@code producerNode}和{@code consumerNode}上产生的伪共享。
     * 划重点：该填充减少了生产者和消费者之间产生的竞争。
     */
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

//$gen:ordered-fields
abstract class BaseLinkedQueueConsumerNodeRef<E> extends BaseLinkedQueuePad1<E>
{
    private final static long C_NODE_OFFSET = fieldOffset(BaseLinkedQueueConsumerNodeRef.class,"consumerNode");

    /**
     * 消费者当前的消费节点 - 队列的头结点。
     * <p>
     * 目前的约束为：
     * 1. 该属性不为null
     * 2. 该节点的value属性总是为null，poll等操作其实是获取下一个节点的value
     * 3. 仅当该属性与{@code producerNode}为同一个节点 或 尚不可达{@code producerNode}时，next才为null。
     */
    private LinkedQueueNode<E> consumerNode;

    /**
     * storePlainConsumerNode
     * 当是单消费者模式时，消费者使用该方法赋值。
     */
    final void spConsumerNode(LinkedQueueNode<E> newValue)
    {
        consumerNode = newValue;
    }

    /**
     * loadVolatileConsumerNode
     * 当不确定是消费者线程时，使用该方法读取。
     */
    @SuppressWarnings("unchecked")
    final LinkedQueueNode<E> lvConsumerNode()
    {
        return (LinkedQueueNode<E>) UNSAFE.getObjectVolatile(this, C_NODE_OFFSET);
    }

    /**
     * loadPlainConsumerNode
     * 当确定是消费者线程时，使用该方法读取即可，因为只有消费者线程更新。
     */
    final LinkedQueueNode<E> lpConsumerNode()
    {
        return consumerNode;
    }
}

abstract class BaseLinkedQueuePad2<E> extends BaseLinkedQueueConsumerNodeRef<E>
{
    /**
     * 缓存行填充，避免{@code consumerNode}产生伪共享。
     */
    byte b000,b001,b002,b003,b004,b005,b006,b007;//  8b
    byte b010,b011,b012,b013,b014,b015,b016,b017;// 16b
    byte b020,b021,b022,b023,b024,b025,b026,b027;// 24b
    byte b030,b031,b032,b033,b034,b035,b036,b037;// 32b
    byte b040,b041,b042,b043,b044,b045,b046,b047;// 40b
    byte b050,b051,b052,b053,b054,b055,b056,b057;// 48b
    byte b060,b061,b062,b063,b064,b065,b066,b067;// 56b
    byte b070,b071,b072,b073,b074,b075,b076,b077;// 64b
    byte b100,b101,b102,b103,b104,b105,b106,b107;// 72b
    byte b110,b111,b112,b113,b114,b115,b116,b117;// 80b
    byte b120,b121,b122,b123,b124,b125,b126,b127;// 88b
    byte b130,b131,b132,b133,b134,b135,b136,b137;// 96b
    byte b140,b141,b142,b143,b144,b145,b146,b147;//104b
    byte b150,b151,b152,b153,b154,b155,b156,b157;//112b
    byte b160,b161,b162,b163,b164,b165,b166,b167;//120b
    byte b170,b171,b172,b173,b174,b175,b176,b177;//128b
}

/**
 * 前面一系列基类通过缓存行填充消除了{@code producerNode}和{@code consumerNode}上产生的伪共享。
 * 从该类开始可以进行一些基本逻辑的实现了。。。。
 * <p>
 * 该类为基于链表的并发队列的基本数据结构实现。为了方便起见，还引入了常见的单消费者方法，因为目前尚无实现MC(多消费者)的计划。
 * <p>
 * 我认为的合理的（队列/对象）状态约束：
 * <ol>
 *     <li>{@code producerNode}与{@code consumerNode}永不为null</li>
 *     <li>{@code consumerNode}不可以跨过{@code producerNode}</li>
 *     <li>{@code consumerNode}的value始终为null</li>
 *     <li>{@code producerNode}的value为null时，表示与{@code consumerNode}为同一个节点，即队列为空。</li>
 *     <li>允许一段时间内{@code producerNode}从{@code consumerNode}不可达，但必须最终可达</li>
 * </ol>
 * <p>
 * 基于{@code consumerNode}不可以跨过{@code producerNode}的约束，那么先读取{@code consumerNode}再读取{@code producerNode}，
 * 就可以确定先读取的{@code consumerNode}不会跨过{@code producerNode}。
 *
 * A base data structure for concurrent linked queues. For convenience also pulled in common single consumer
 * methods since at this time there's no plan to implement MC.
 */
abstract class BaseLinkedQueue<E> extends BaseLinkedQueuePad2<E>
{

    @Override
    public final Iterator<E> iterator()
    {
        // 这里选择了不支持迭代，理论上可以支持(理论同size方法实现)，但是就算支持，也是一个不精确的遍历
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }

    protected final LinkedQueueNode<E> newNode()
    {
        return new LinkedQueueNode<E>();
    }

    protected final LinkedQueueNode<E> newNode(E e)
    {
        return new LinkedQueueNode<E>(e);
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 实现提示：<br>
     * 由于我们通过遍历所有节点对其进行计数，因此这是一个O(n)的操作。<br>
     * 该方法返回值的精确性受制于生产者/消费者的竞争，尤其是与消费者产生竞争时，该方法总是一个估算值，而不是精确值。
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * This is an O(n) operation as we run through all the nodes and count them.<br>
     * The accuracy of the value returned by this method is subject to races with producer/consumer threads. In
     * particular when racing with the consumer thread this method may under estimate the size.<br>
     *
     * @see java.util.Queue#size()
     */
    @Override
    public final int size()
    {
        // 首先读取消费者节点，这很重要！
        // 因为如果生产者节点是一个比消费者节点更旧的值，则消费者节点可能已经越过了该生产者节点（已消费该节点），从而导致基于快照概念的size无效。

        // 解释一下：
        // 简单的size计算可以从头结点开始遍历，一直到next为null或self。但是在并发环境下，size本就是一个估算值，因此我们可以降低精确性，从而减少开销。
        // Q: 那么怎么做呢？
        // A: 我们改变遍历结束条件，我们将当前的头结点和尾节点存为快照，从快照的头结点遍历到快照的尾节点，我们返回这个快照对应的size即可。
        // 那么在存储快照的时候就必须保证 尾节点（生产者）不可以比头结点（消费者）更旧，否则尾节点可能是无效值。这需要满足两方面的保证:
        // 1. 子类实现必须保证消费时consumerNode不会越过producerNode（类文档中的状态约束）
        // 2. 两个读操作之间不可以重排序 - volatile读不会与后面的读写操作重排序，在J9中也可以在两者间插入acquireFence

        // Read consumer first, this is important because if the producer is node is 'older' than the consumer
        // the consumer may overtake it (consume past it) invalidating the 'snapshot' notion of size.
        LinkedQueueNode<E> chaserNode = lvConsumerNode();
        LinkedQueueNode<E> producerNode = lvProducerNode();

        // 在这里注释是为了避免对有效代码行产生增量（尽量保持增量为绿色）
        // 1. chaserNode != producerNode 还未到达快照的尾节点
        // 2. chaserNode != null 消费者节点与当前生产者节点之间可能尚不可达（但最终可达），此时可能为null；也可能节点已被删除（remove）或 正在删除过程中;
        // 3. next == chaserNode 消费者在消费节点时，会将next置为self，从而触发该条件，此时必须中断循环(否则死循环)

        int size = 0;
        // must chase the nodes all the way to the producer node, but there's no need to count beyond expected head.
        while (chaserNode != producerNode && // don't go passed producer node
            chaserNode != null && // stop at last node
            size < Integer.MAX_VALUE) // stop at max int
        {
            LinkedQueueNode<E> next;
            next = chaserNode.lvNext();
            // check if this node has been consumed, if so return what we have
            if (next == chaserNode)
            {
                return size;
            }
            chaserNode = next;
            size++;
        }
        return size;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 实现方式提示：<br>
     * 当生产者节点与消费者节点相同时，队列为空。
     * 另一种实现方式是观察producerNode.value为null，这也意味着队列为空，因为只允许consumerNode.value为null。
     * 注意：理论上只有消费者才可以安全的访问value，其它线程都不具备安全访问value的保证，所以观察producerNode.value不通用。
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Queue is empty when producerNode is the same as consumerNode. An alternative implementation would be to
     * observe the producerNode.value is null, which also means an empty queue because only the
     * consumerNode.value is allowed to be null.
     *
     * @see MessagePassingQueue#isEmpty()
     */
    @Override
    public boolean isEmpty()
    {
        // 只有当实现类满足{@code consumerNode}不可以跨过{@code producerNode}的约束时，这里才是正确的
        // 此时先读取{@code consumerNode}再读取{@code producerNode}，就可以确定先读取的{@code consumerNode}不会跨过{@code producerNode}

        LinkedQueueNode<E> consumerNode = lvConsumerNode();
        LinkedQueueNode<E> producerNode = lvProducerNode();
        return consumerNode == producerNode;
    }

    /**
     * 这个方法名实在是有点差，不能表达实际逻辑，实际逻辑如下：
     * 1. 清除{@code currConsumerNode}到{@code nextNode}的引用
     * 2. 将{@code consumerNode}更新为{@code nextNode}
     * 3. 清除{@code nextNode}对value的引用并返回value的值
     * <p>
     * 因此不允许插入空值，而这是唯一一次将value设置为null的节点，可以确保consumerNode.value始终为null。
     */
    protected E getSingleConsumerNodeValue(LinkedQueueNode<E> currConsumerNode, LinkedQueueNode<E> nextNode)
    {
        // 因为我们将挂接到nextNode上，因此必须将value置为null，使用普通模式的读写，是因为已经假设为单消费者了。
        // Q: 如果value不置为null会发生什么？
        // A: 因为consumerNode会被更新为nextNode，因此nextNode会被（长时间）引用，如果不将value置为null，则value会因为consumerNode的引用而泄漏。

        // we have to null out the value because we are going to hang on to the node
        final E nextValue = nextNode.getAndNullValue();

        // 断开当前消费者节点到nextNode的链接，并更新消费者节点为nextNode
        // 修复currConsumerNode的next引用，以防止增加nextNode的存活时间
        // 我们使用对self而不是null的引用，因为null已经是一个有意义的值（生产者节点的下一个为null）

        // Fix up the next ref of currConsumerNode to prevent promoted nodes from keeping new ones alive.
        // We use a reference to self instead of null because null is already a meaningful value (the next of
        // producer node is null).
        currConsumerNode.soNext(currConsumerNode);
        spConsumerNode(nextNode);
        // currConsumerNode is now no longer referenced and can be collected
        return nextValue;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 实现说明：<br>
     * Poll方法仅允许单个线程（消费者线程）调用。<br>
     * {@link Queue#poll()}约定，当队列不为空时，不允许返回null，因此Poll方法在这里存在潜在的阻塞。
     * 这与原始的Vyukov设计中提供的保证有很大的不同。关于原始语义，请查看{@link #relaxedPoll()}。<br>
     * Poll方法读取{@code consumerNode.next}，然后：
     * <ol>
     * <li>如果next为{@code null}并且队列为空，则返回null，如果队列不为空，则自旋等待直到value可见(直到next不为null)</li>
     * <li>如果next不为{@code null},则将其更新为当前的consumerNode，并返回它排空的value（evacuated不太好翻译，其实表达的是next不在引用该value）</li>
     * </ol>
     * 这意味着consumerNode.value始终为null，同时也是队列的起点。因为不允许插入null，而这里是唯一一次将其值设置为null的节点。
     *
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Poll is allowed from a SINGLE thread.<br>
     * Poll is potentially blocking here as the {@link Queue#poll()} does not allow returning {@code null} if the queue is not
     * empty. This is very different from the original Vyukov guarantees. See {@link #relaxedPoll()} for the original
     * semantics.<br>
     * Poll reads {@code consumerNode.next} and:
     * <ol>
     * <li>If it is {@code null} AND the queue is empty return {@code null}, <b>if queue is not empty spin wait for
     * value to become visible</b>.
     * <li>If it is not {@code null} set it as the consumer node and return it's now evacuated value.
     * </ol>
     * This means the consumerNode.value is always {@code null}, which is also the starting point for the queue.
     * Because {@code null} values are not allowed to be offered this is the only node with it's value set to
     * {@code null} at any one time.
     *
     * @see MessagePassingQueue#poll()
     * @see java.util.Queue#poll()
     */
    @Override
    public E poll()
    {
        final LinkedQueueNode<E> currConsumerNode = lpConsumerNode();
        LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null)
        {
            // next属性不为null，表示未到达队尾
            // 注意：这里未检查producerNode，这需要生产者保证next可见时，producerNode已完成更新，这样consumerNode将不会越过producerNode。
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        else if (currConsumerNode != lvProducerNode())
        {
            // nextNode == null，表示我们currConsumerNode为生产者节点，
            // currConsumerNode != lvProducerNode()，表示currConsumerNode是一个旧的生产者节点，也就是说即将有生产者将新节点连接到currConsumerNode
            // 因此通过自旋等待，我们一定可以看见一个新的节点。
            // 但等待的时间是不确定的，这里假设很快完成，因此纯粹的自旋，这也是比relaxedPoll开销大的主要原因。
            nextNode = spinWaitForNextNode(currConsumerNode);
            // got the next node...
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        // 队列为空
        return null;
    }

    /**
     * {@inheritDoc} <br>
     * <p>
     * 这里的实现可参考{@link #poll()}，原理基本一致（原始的英文doc也是拷贝的，都能看见Poll...）。<br>
     * 实现说明：<br>
     * Peek方法仅允许单个线程（消费者线程）调用。<br>
     * {@link Queue#peek()}约定，当队列不为空时，不允许返回null，因此Peek方法在这里存在潜在的阻塞。
     * 这与原始的Vyukov设计中提供的保证有很大的不同。关于原始语义，请查看{@link #relaxedPeek()}。<br>
     * Peek方法读取{@code consumerNode.next}，然后：
     * <ol>
     * <li>如果next为{@code null}并且队列为空，则返回null，如果队列不为空，则自旋等待直到value可见(直到next不为null)</li>
     * <li>如果next不为{@code null},则返回它的value（next仍然引用该value）</li>
     * </ol>
     * <p>
     * IMPLEMENTATION NOTES:<br>
     * Peek is allowed from a SINGLE thread.<br>
     * Peek is potentially blocking here as the {@link Queue#peek()} does not allow returning {@code null} if the queue is not
     * empty. This is very different from the original Vyukov guarantees. See {@link #relaxedPeek()} for the original
     * semantics.<br>
     * Poll reads the next node from the consumerNode and:
     * <ol>
     * <li>If it is {@code null} AND the queue is empty return {@code null}, <b>if queue is not empty spin wait for
     * value to become visible</b>.
     * <li>If it is not {@code null} return it's value.
     * </ol>
     *
     * @see MessagePassingQueue#peek()
     * @see java.util.Queue#peek()
     */
    @Override
    public E peek()
    {
        final LinkedQueueNode<E> currConsumerNode = lpConsumerNode();
        LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null)
        {
            // next属性不为null，表示未到达队尾
            return nextNode.lpValue();
        }
        else if (currConsumerNode != lvProducerNode())
        {
            // nextNode == null，表示我们currConsumerNode为生产者节点，
            // currConsumerNode != lvProducerNode()，表示currConsumerNode是一个旧的生产者节点，也就是说即将有生产者将新节点连接到currConsumerNode
            // 因此通过自旋等待，我们一定可以看见一个新的节点。
            // 但等待的时间是不确定的，这里假设很快完成，因此纯粹的自旋，这也是比relaxedPeek开销大的主要原因。

            nextNode = spinWaitForNextNode(currConsumerNode);
            // got the next node...
            return nextNode.lpValue();
        }
        return null;
    }

    /**
     * 自旋等待直到Node的next属性可见
     * Q: 为什么要等待？
     * A: 因为{@link LinkedQueueNode#soNext(LinkedQueueNode)}使用的是Ordered模式，因此并不保证next属性的立即可见性。
     * 我们可能通过观察其它属性知道其它线程对next进行了赋值，因此我们自旋等待，直到next属性对当前线程可见。
     */
    LinkedQueueNode<E> spinWaitForNextNode(LinkedQueueNode<E> currNode)
    {
        LinkedQueueNode<E> nextNode;
        while ((nextNode = currNode.lvNext()) == null)
        {
            // spin, we are no longer wait free
        }
        return nextNode;
    }

    @Override
    public E relaxedPoll()
    {
        // 宽松的poll，这种模式下，并未尽最大努力去判断是否还有元素（可以避免潜在的阻塞）
        final LinkedQueueNode<E> currConsumerNode = lpConsumerNode();
        final LinkedQueueNode<E> nextNode = currConsumerNode.lvNext();
        if (nextNode != null)
        {
            // next属性不为null，表示未到达队尾
            // 注意：这里未检查producerNode，这需要生产者保证next可见时，producerNode已完成更新，这样consumerNode将不会越过producerNode。
            return getSingleConsumerNodeValue(currConsumerNode, nextNode);
        }
        // nextNode == null 表示队列为空 或 下一个节点尚不可达
        return null;
    }

    @Override
    public E relaxedPeek()
    {
        // 宽松的peek，这种模式下，并未尽最大努力去判断是否还有元素（可以避免潜在的阻塞）
        final LinkedQueueNode<E> nextNode = lpConsumerNode().lvNext();
        if (nextNode != null)
        {
            // next属性不为null，表示未到达队尾
            return nextNode.lpValue();
        }
        // nextNode == null 表示队列为空 或 下一个节点尚不可达
        return null;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        // 由于是无界队列，并不存在队列已满的情况，因此与offer并无区别
        return offer(e);
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        if (null == c)
            throw new IllegalArgumentException("c is null");
        if (limit < 0)
            throw new IllegalArgumentException("limit is negative: " + limit);
        if (limit == 0)
            return 0;

        // 因为是已假定是单消费者，因此当消费者读写consumerNode时只需要普通模式读写
        LinkedQueueNode<E> chaserNode = this.lpConsumerNode();
        for (int i = 0; i < limit; i++)
        {
            final LinkedQueueNode<E> nextNode = chaserNode.lvNext();

            // nextNode为null表示chaserNode为生产者节点，但是chaserNode可能是一个旧的生产者节点，
            // 并未尝试读取最新的生产者节点进行判断，因为接口中drain的语义描述为使用relaxedPoll。
            if (nextNode == null)
            {
                return i;
            }
            // we have to null out the value because we are going to hang on to the node
            final E nextValue = getSingleConsumerNodeValue(chaserNode, nextNode);
            chaserNode = nextNode;
            c.accept(nextValue);
        }
        return limit;
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public int capacity()
    {
        // 无界队列，因此直接返回无界队列的默认值 -1
        return UNBOUNDED_CAPACITY;
    }

}
