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

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

final class LinkedQueueNode<E>
{
    private final static long NEXT_OFFSET = fieldOffset(LinkedQueueNode.class,"next");

    private E value;
    /**
     * 下一个节点的指针
     * <p>
     * <li>如果为null，表示已经到底队列的尾部，当前Node为生产者节点 或 当前节点已从队列中删除</li>
     * <li>如果为this，表示当前节点已经被消费</li>
     */
    private volatile LinkedQueueNode<E> next;

    LinkedQueueNode()
    {
        this(null);
    }

    LinkedQueueNode(E val)
    {
        spValue(val);
    }

    /**
     * Gets the current value and nulls out the reference to it from this node.
     *
     * @return value
     */
    public E getAndNullValue()
    {
        E temp = lpValue();
        spValue(null);
        return temp;
    }

    /**
     * loadPlainValue - 普通方式读取value的值
     */
    public E lpValue()
    {
        return value;
    }

    /**
     * storePlainValue - 普通方式对value赋值
     */
    public void spValue(E newValue)
    {
        value = newValue;
    }

    /**
     * storeOrderedNext - 使用Ordered方式对next赋值
     * <p>
     * 解释：在对next的存储之前会插入内存屏障(StoreStore + LoadStore)，保证对<b>next的赋值及之后的写操作</b>不会与前面的读写指令进行重排序，
     * 因此当{@link #lvNext()}读取到next的值时，可以获得next赋值前写操作的可见性。
     * Ordered类似削弱版的volatile(不保证对其它线程的立即可见性)，建议看看J9的VarHandle或者UnSafe源码的文档。
     */
    public void soNext(LinkedQueueNode<E> n)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, n);
    }

    /**
     * storePlainNext
     * 在批量插入的时候，可以依靠后续的{@code soProducerNode}实现安全发布
     */
    public void spNext(LinkedQueueNode<E> n)
    {
        UNSAFE.putObject(this, NEXT_OFFSET, n);
    }

    /**
     * loadVolatileNext - 使用volatile语义读取next的值
     */
    public LinkedQueueNode<E> lvNext()
    {
        return next;
    }
}
