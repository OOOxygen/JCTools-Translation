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

import java.util.Collection;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 消息传输队列
 * <p>
 * 消息传输队列用于并发方式的消息传递。与{@link Queue}中方法的子集提供了相同的语义，此外还提供了满足并发用例的其它方法。
 * <p>
 * 消息传输队列的消息传输提供了happens-before语义，即：对于一个特定的消息，生产者在将其压入队列之前的写操作对于稍后使用poll从队列中取出该消息的消费者是可见的。
 * <p>
 * 注意：如果实现了happens-before语义，那么offer之前读写操作都先于后一个线程的poll操作，而不仅仅是写操作的可见性，如果仅仅提供写操作的可见性则不是happens-before。
 * 这里只是简单地认为JCTools想强调写操作的可见性。
 *
 * Message passing queues are intended for concurrent method passing. A subset of {@link Queue} methods are provided
 * with the same semantics, while further functionality which accomodates the concurrent usecase is also on offer.
 * <p>
 * Message passing queues provide happens before semantics to messages passed through, namely that writes made
 * by the producer before offering the message are visible to the consuming thread after the message has been
 * polled out of the queue.
 *
 * @param <T> the event/message type
 */
public interface MessagePassingQueue<T>
{
    /**
     * 当队列实现为无界队列时，{@link #capacity()}返回该值。
     */
    int UNBOUNDED_CAPACITY = -1;

    /**
     * 生产者可以通过该接口向队列中填充消息。
     */
    interface Supplier<T>
    {
        /**
         * 该方法用于创建下一个写入队列的值。因此，一旦该方法被调用，队列的实现将提交以插入队列。
         * <p>
         * 用户应该意识到底层队列的实现可能会预先声明队列的部分以进行批量操作，这可能会影响{@link Supplier}方法在队列上的视图。
         * 特别是{@code size}和任意的{@code offer}方法，可能认为整个批量操作已完成(实际只是提前创建了消息)。
         * Q: 这是个什么意思？
         * A: 举个栗子，当队列是无界队列的时候，如果生产者期望插入100个元素，那么队列实现可以先构建这100个元素，将它们链接起来，然后进行一次插入，而不是100次插入。
         * 这样可以极大的提高性能，当然运用场景也不是那么常见。
         * <p>
         * 警告：队列假设方法永远不会抛出异常。如果打破这一假设，则可能导致队列中断 - 生产者序列被中断，消费者将无法感知到后续的消息，将死锁。<br>
         * 警告：队列假设方法永远不会返回null。如果打破这一假设，则可能导致队列中断 - eg: 如果消费者总是使用peek是否为null判断有无消息到达，可能死锁。<br>
         * 警告：该实现应当是轻量级的，不会长时间阻塞，否则可能影响其它生产者 - 这是我(翻译者)给的警告，生产者之间可能会竞争索引，
         * 如果先竞争到索引的生产者阻塞了，其它生产者发布的数据也无法被立即消费。
         * <p>
         * PS: JCTools的很多实现都不是防御式的。比如这里，如果采用一层代理的话，可以对结果进行检查，从而避免队列被破坏。
         * 但是很多地方难以检查，比如单消费者(或单生产者)，使用者应该遵循这些契约。
         * <p>
         * 接口名与JDK8的{@link java.util.function.Supplier}相同，方法名相同，表达的含义也是一样。
         *
         * This method will return the next value to be written to the queue. As such the queue
         * implementations are commited to insert the value once the call is made.
         * <p>
         * Users should be aware that underlying queue implementations may upfront claim parts of the queue
         * for batch operations and this will effect the view on the queue from the supplier method. In
         * particular size and any offer methods may take the view that the full batch has already happened.
         *
         * <p><b>WARNING</b>: this method is assumed to never throw. Breaking this assumption can lead to a broken queue.
         * <p><b>WARNING</b>: this method is assumed to never return {@code null}. Breaking this assumption can lead to a broken queue.
         *
         * @return new element, NEVER {@code null}
         */
        T get();
    }

    /**
     * 消费者，消费可以使用该接口消费队列中的元素
     */
    interface Consumer<T>
    {
        /**
         * 该方法用于处理已从队列中删除(poll)的元素。队列期望该方法不会抛出异常。
         * <p>
         * 用户应该意识到，底层的队列实现可能预先声明队列中的部分以进行批量操作，这可能会影响accept方法在队列上的视图。
         * 特别是{@code size}和{@code poll/peek}方法可能会认为整个批次已完成(实际只是提前拉取了消息)。
         * Q: 这是什么意思呢？
         * A: 举个栗子：当我们期望消费100个元素时，那么队列的实现可能会先将这100个元素‘拉取’下来，然后再进行消费，这可以大大减少竞争。
         * <p>
         * 警告：队列假设该方法永远不会抛出异常。如果打破这一假设则可能导致队列中断 - 如果在进行批量消费的时候抛出异常，可能造成消费者丢失消息。
         *
         * <p>
         * This method will process an element already removed from the queue. This method is expected to
         * never throw an exception.
         * <p>
         * Users should be aware that underlying queue implementations may upfront claim parts of the queue
         * for batch operations and this will effect the view on the queue from the accept method. In
         * particular size and any poll/peek methods may take the view that the full batch has already
         * happened.
         *
         * <p><b>WARNING</b>: this method is assumed to never throw. Breaking this assumption can lead to a broken queue.
         * @param e not {@code null}
         */
        void accept(T e);
    }

    /**
     * 等待策略
     * 生产者在队列已满的时候使用该策略进行等待。
     * 消费者在队列为空的时候使用该策略进行等待。
     *
     * 扩展: 在disruptor目前版本中生产者尚未支持等待策略和退出策略，因此生产者调用next(int)可能死锁，不过disruptor提供了较多的消费者等待策略实现。
     */
    interface WaitStrategy
    {
        /**
         * 该方法可以实现为静态的或动态退让（算法）。动态退让算法可能依赖于(等待)计数器估算调用者已空闲的时间。
         *
         * This method can implement static or dynamic backoff. Dynamic backoff will rely on the counter for
         * estimating how long the caller has been idling. The expected usage is:
         * <p>
         * <pre>
         * <code>
         * int ic = 0;
         * while(true) {
         *   if(!isGodotArrived()) {
         *     ic = w.idle(ic);
         *     continue;
         *   }
         *   ic = 0;
         *   // party with Godot until he goes again
         * }
         * </code>
         * </pre>
         *
         * @param idleCounter idle calls counter, managed by the idle method until reset
         * @return new counter value to be used on subsequent idle cycle
         */
        int idle(int idleCounter);
    }

    /**
     * 退出条件，它总是和{@link WaitStrategy}一起使用，用于判断是否继续等待。
     */
    interface ExitCondition
    {

        /**
         * 该方法应实现为：标记变量的读或循环条件不会被提升到循环之外（被提升到循环外会造成死循环）。
         * 这意味着一个非正式的volatile读，在JDK9的VarHandles中意味着getOpaque。
         * （Opaque模式写的话，可以保证这个值最终对其它线程可见，其他线程至少需要Opaque模式读，但对周围的变量并无作用）
         * 警告：并不建议使用过弱的模式，Opaque模式是一个用处很有限的模式。
         *
         * This method should be implemented such that the flag read or determination cannot be hoisted out of
         * a loop which notmally means a volatile load, but with JDK9 VarHandles may mean getOpaque.
         *
         * @return true as long as we should keep running
         */
        boolean keepRunning();
    }

    /**
     * 从生产者线程调用，必须遵守适合实现类的限制以及{@link Queue#offer(Object)}约定。
     * <p>
     * 警告：使用者必须严格遵守实现类的限制。举个栗子：如果队列为 单生产者 队列，那么使用者必须遵守该限制，否则队列会被静默的破坏。
     * 因为队列的实现并不能确定当前线程是否合法，因此使用者万万小心。
     *
     * Called from a producer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#offer(Object)} interface.
     *
     * @param e not {@code null}, will throw NPE if it is
     * @return true if element was inserted into the queue, false iff full
     */
    boolean offer(T e);

    /**
     * 从消费者线程调用，必须遵守实现类的限制以及{@link Queue#poll()}约定。
     * <p>
     * 警告：使用者必须严格遵守实现类的限制，举个栗子：如果队列为 单消费者队列，那么使用者必须遵守该限制，否则队列会被静默的破坏。
     * 因为队列的实现并不能确定当前线程是否合法，因此使用者万万小心。
     *
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#poll()} interface.
     *
     * @return a message from the queue if one is available, {@code null} iff empty
     */
    T poll();

    /**
     * 从消费者线程调用，必须遵守实现类的限制以及{@link Queue#peek()}约定。
     * <p>
     * 警告：理论上该方法的要求低于{@link #poll()}，因为不会破坏队列，但是最好还是遵守{@link #poll()}相同的约定。
     *
     * Called from the consumer thread subject to the restrictions appropriate to the implementation and
     * according to the {@link Queue#peek()} interface.
     *
     * @return a message from the queue if one is available, {@code null} iff empty
     */
    T peek();

    /**
     * 该方法的精确性可能受到同时发生的并发修改的影响，因此size是一个尽力而为的估算值，而不是一个准确值。
     * 注意：对于部分实现，该方法的时间复杂度可能为 O(n) 而不是O(1)。
     * 扩展：1. {@link ConcurrentLinkedQueue#size()}就是O(n)。
     *      2. 对于一个并发容器，由于并发修改的存在，size和{@link #isEmpty()}往往都是旧值，所以编程时尽量少出现先检查后执行逻辑。
     *
     * This method's accuracy is subject to concurrent modifications happening as the size is estimated and as
     * such is a best effort rather than absolute value. For some implementations this method may be O(n)
     * rather than O(1).
     *
     * @return number of messages in the queue, between 0 and {@link Integer#MAX_VALUE} but less or equals to
     * capacity (if bounded).
     */
    int size();

    /**
     * 清除队列中的所有元素。从消费者线程调用，必须遵守实现类的限制和{@link Queue#clear()}的约定。
     * <p>
     * 警告：必须遵守{@link #poll()}相同的约定，否则会使队列处于无效状态。
     *
     * Removes all items from the queue. Called from the consumer thread subject to the restrictions
     * appropriate to the implementation and according to the {@link Queue#clear()} interface.
     */
    void clear();

    /**
     * 同{@link #size()}一样，该方法的精确性受到同时发生的并发修改的影响。
     *
     * This method's accuracy is subject to concurrent modifications happening as the observation is carried
     * out.
     *
     * @return true if empty, false otherwise
     */
    boolean isEmpty();

    /**
     * 返回队列的容量，如果队列为无界队列，那么返回{@link {@link MessagePassingQueue#UNBOUNDED_CAPACITY} }。
     *
     * @return the capacity of this queue or {@link MessagePassingQueue#UNBOUNDED_CAPACITY} if not bounded
     */
    int capacity();

    /**
     * 由生产者线程调用，必须遵守实现类的限制。与{@link Queue#offer(Object)}不同，该方法可能返回false而队列未满。
     * <p>
     * Q: 该方法有什么用？
     * A: 该方法放松了对{@link #offer(Object)}的要求，可以大大减少竞争（边界情况下的竞争），以获得更高的吞吐量。
     * <p>
     * Q: 为什么这个优化是合理的？
     * A: 对于并发容器，如果不包含完整的互斥（锁），对空间的限制一定是不精确的。鉴于此，我们可以进一步放宽限制，允许合理的预估，以快速返回，而不是大量的重试（竞争）。
     *
     * Called from a producer thread subject to the restrictions appropriate to the implementation. As opposed
     * to {@link Queue#offer(Object)} this method may return false without the queue being full.
     *
     * @param e not {@code null}, will throw NPE if it is
     * @return true if element was inserted into the queue, false if unable to offer
     */
    boolean relaxedOffer(T e);

    /**
     * 由消费者调用，必须遵守实现类的限制。与{@link Queue#poll()}不同，该方法可能返回null而队列不为空。
     * 作用同{@link #relaxedOffer(Object)}，用于减少竞争，以获得更高的吞吐量。
     * <p>
     * 这里我拿订外卖举例，帮助大家快速理解{@code poll}和{@code relaxedPoll}的概念。<br>
     * 为了和这里更匹配，假设送餐员只是将外卖放到前台，然后我们不断的去前台看外卖是否到了。<br>
     * poll可以这样理解：我去前台，发现外卖到了，取餐返回。外卖还没到，打开手机，查看一下订单，如果送餐员已经到达附近了，那么便在前台那里继续等待，直到送餐员到达，然后返回，否则直接返回。<br>
     * relaxedPoll可以这样理解：我去前台，发现外卖到了，取餐返回。发现外卖还没到，就直接返回了。<br>
     * <p>
     * 通过上面的例子，我们可以知道poll会观察更多的信息，以更精确的判断队列是否为空，而relaxedPoll则会降低精确度。
     * 但是也可以看出poll会做更多的活，此外还存在等待的情况，而这个等待的时间也是不确定的，在高并发下会产生较多的额外开销（性能浪费）。
     *
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As
     * opposed to {@link Queue#poll()} this method may return {@code null} without the queue being empty.
     *
     * @return a message from the queue if one is available, {@code null} if unable to poll
     */
    T relaxedPoll();

    /**
     * 由消费者调用，必须遵守实现类的限制。与{@link Queue#peek()}不同，该方法可能返回null而队列不为空。
     * 作用同{@link #relaxedPoll()}，用于减少竞争，以获得更高的吞吐量。
     *
     * Called from the consumer thread subject to the restrictions appropriate to the implementation. As
     * opposed to {@link Queue#peek()} this method may return {@code null} without the queue being empty.
     *
     * @return a message from the queue if one is available, {@code null} if unable to peek
     */
    T relaxedPeek();

    /**
     * 从队列中最多删除 <i>limit</i>个元素并进行消费。
     * 该方法的语义<b>类似于</b>for循环调用{@link #relaxedPoll()}，并调用{@link Consumer#accept(Object)}方法 - 注意：是类似而不是等同。
     * <p>
     * 注意：这里并不提供drain结束时队列为空的强的保证。由消费者线程调用，必须遵守实现类的限制。
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读并理解了对{@link Consumer#accept(Object)}方法的明确假设（一定要看）。
     * <p>
     * Q: 该方法有什么用？
     * A: 在{@link Consumer}中有过解释，这是对批量消费进行优化，如果简单地使用for循环进行消费，每一次删除元素都会产生一次竞争，
     * 而使用该方法时，具体的实现类可以选择对其优化，批量地声明可消费的元素，以大大减少竞争。
     * <p>
     * 拓展：{@link BlockingQueue#drainTo(Collection)}
     *
     * Remove up to <i>limit</i> elements from the queue and hand to consume. This should be semantically
     * similar to:
     * <p>
     * <pre>{@code
     *   M m;
     *   int i = 0;
     *   for(;i < limit && (m = relaxedPoll()) != null; i++){
     *     c.accept(m);
     *   }
     *   return i;
     * }</pre>
     * <p>
     * There's no strong commitment to the queue being empty at the end of a drain. Called from a consumer
     * thread subject to the restrictions appropriate to the implementation.
     * <p>
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Consumer#accept} make sure you have read
     * and understood these before using this method.
     *
     * @return the number of polled elements 真正消费的元素个数
     * @throws IllegalArgumentException c is {@code null}
     * @throws IllegalArgumentException if limit is negative
     */
    int drain(Consumer<T> c, int limit);

    /**
     * 使用给定的supplier填充至多<i>limit</i>个元素到队列。它的语义类似for循环调用relaxOffer压入元素。
     * <p>
     * 这里并不提供fill方法返回时队列已满的强保证。由生产者调用，并且需要遵守实现类的限制。
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读和理解了对于{@link Supplier#get()}方法的假设（一定要看）。
     *
     * Stuff the queue with up to <i>limit</i> elements from the supplier. Semantically similar to:
     * <p>
     * <pre>{@code
     *   for(int i=0; i < limit && relaxedOffer(s.get()); i++);
     * }</pre>
     * <p>
     * There's no strong commitment to the queue being full at the end of a fill. Called from a producer
     * thread subject to the restrictions appropriate to the implementation.
     *
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Supplier#get} make sure you have read
     * and understood these before using this method.
     *
     * @return the number of offered elements 真正填充的元素个数
     * @throws IllegalArgumentException s is {@code null}
     * @throws IllegalArgumentException if limit is negative
     */
    int fill(Supplier<T> s, int limit);

    /**
     * 从队列中删除所有可用的元素，并交给消费者消耗。其语义类似于for循环调用{@link #relaxedPoll()}消费。
     * <p>
     * 注意：这里并不提供drain结束时队列为空的强的保证。由消费者线程调用，必须遵守实现类的限制。
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读并理解了对{@link Consumer#accept(Object)}方法的明确假设（一定要看）。
     *
     * Remove all available item from the queue and hand to consume. This should be semantically similar to:
     * <pre>
     * M m;
     * while((m = relaxedPoll()) != null){
     * c.accept(m);
     * }
     * </pre>
     * There's no strong commitment to the queue being empty at the end of a drain. Called from a
     * consumer thread subject to the restrictions appropriate to the implementation.
     * <p>
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Consumer#accept} make sure you have read
     * and understood these before using this method.
     *
     * @return the number of polled elements 真正消费的元素个数
     * @throws IllegalArgumentException c is {@code null}
     */
    int drain(Consumer<T> c);

    /**
     * 使用给定的supplier填元素到队列，直到队列已满。它的语义类似for循环调用relaxOffer压入元素。
     * <p>
     * 这里并不提供fill方法返回时队列已满的强保证。由生产者调用，并且需要遵守实现类的限制。
     * <p>
     * 对于无界队列，将会填充固定数量的元素而不是无限制的填充。
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读和理解了对于{@link Supplier#get()}方法的假设（一定要看）。
     *
     * Stuff the queue with elements from the supplier. Semantically similar to:
     * <pre>
     * while(relaxedOffer(s.get());
     * </pre>
     * There's no strong commitment to the queue being full at the end of a fill. Called from a
     * producer thread subject to the restrictions appropriate to the implementation.
     * <p>
     * Unbounded queues will fill up the queue with a fixed amount rather than fill up to oblivion.
     *
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Supplier#get} make sure you have read
     * and understood these before using this method.
     *
     * @return the number of offered elements 真正填充的元素个数
     * @throws IllegalArgumentException s is {@code null}
     */
    int fill(Supplier<T> s);

    /**
     * 不断地从队列中删除可用的元素，并交给消费者消耗。
     * <p>
     * 由消费者线程调用，并遵守实现类的限制。
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读并理解了对{@link Consumer#accept(Object)}方法的明确假设（一定要看）。
     *
     * Remove elements from the queue and hand to consume forever. Semantically similar to:
     * <p>
     * <pre>
     *  int idleCounter = 0;
     *  while (exit.keepRunning()) {
     *      E e = relaxedPoll();
     *      if(e==null){
     *          idleCounter = wait.idle(idleCounter);
     *          continue;
     *      }
     *      idleCounter = 0;
     *      c.accept(e);
     *  }
     * </pre>
     * <p>
     * Called from a consumer thread subject to the restrictions appropriate to the implementation.
     * <p>
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Consumer#accept} make sure you have read
     * and understood these before using this method.
     *
     * @param wait 当队列中没有可消费的元素时，执行的等待策略
     * @param exit 当队列中没有可消费的元素时，判断是否退出
     *
     * @throws IllegalArgumentException c OR wait OR exit are {@code null}
     */
    void drain(Consumer<T> c, WaitStrategy wait, ExitCondition exit);

    /**
     * 不断地使用给定的supplier填充元素到队列中。
     * <p>
     * 有生产者线程调用，并遵守实现类的限制。主要区别在于实现类必须保证队列里有可用空间在调用{@link Supplier#get()}之前。（但是给的代码似乎不是这么的啊...）
     * <p>
     * 警告：在使用该方法之前，请确保你已经阅读和理解了对于{@link Supplier#get()}方法的假设（一定要看）。
     *
     * Stuff the queue with elements from the supplier forever. Semantically similar to:
     * <p>
     * <pre>
     * <code>
     *  int idleCounter = 0;
     *  while (exit.keepRunning()) {
     *      E e = s.get();
     *      while (!relaxedOffer(e)) {
     *          idleCounter = wait.idle(idleCounter);
     *          continue;
     *      }
     *      idleCounter = 0;
     *  }
     * </code>
     * </pre>
     * <p>
     * Called from a producer thread subject to the restrictions appropriate to the implementation. The main difference
     * being that implementors MUST assure room in the queue is available BEFORE calling {@link Supplier#get}.
     *
     * <b>WARNING</b>: Explicit assumptions are made with regards to {@link Supplier#get} make sure you have read
     * and understood these before using this method.
     *
     * @param wait 当队列已满时，执行的等待策略
     * @param exit 当队列已满时，判断是否退出
     * @throws IllegalArgumentException s OR wait OR exit are {@code null}
     */
    void fill(Supplier<T> s, WaitStrategy wait, ExitCondition exit);
}
