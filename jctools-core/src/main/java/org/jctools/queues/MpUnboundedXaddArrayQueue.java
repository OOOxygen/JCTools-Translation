package org.jctools.queues;

import org.jctools.queues.IndexedQueueSizeUtil.IndexedQueue;
import org.jctools.util.PortableJvmInfo;
import org.jctools.util.Pow2;
import org.jctools.util.UnsafeAccess;

import java.util.AbstractQueue;
import java.util.Iterator;

import static org.jctools.queues.MpUnboundedXaddChunk.NOT_USED;
import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;

abstract class MpUnboundedXaddArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueue
{
    /**
     * 缓存行填充，保护下面的{@code producerIndex}
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

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueProducerFields<E> extends MpUnboundedXaddArrayQueuePad1<E>
{
    private final static long P_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerFields.class, "producerIndex");

    /**
     * 生产者索引。
     * 生产者先根据index计算当前应该填充的chunk的索引（编号），也根据index计算落在该chunk的哪个槽位，非常巧的设计。
     * 这仍然是一个预更新值，因为是多生产者模型。
     */
    private volatile long producerIndex;

    /**
     * loadVolatileProducerIndex
     * 因为是多生产者模式，因此都需要volatile模式读取
     */
    @Override
    public final long lvProducerIndex()
    {
        return producerIndex;
    }

    /**
     * 生产者不再考虑消费者进度，生产者索引+1后直接填充对应的槽位。
     * 由于彻底不考虑消费者进度，也就无producerLimit，这就减少了许多竞争，因此比{@link MpscUnboundedArrayQueue}有更好的性能（吞吐量）。
     */
    final long getAndIncrementProducerIndex()
    {
        return UNSAFE.getAndAddLong(this, P_INDEX_OFFSET, 1);
    }

    /**
     * {@link #getAndIncrementProducerIndex()}的批量版本
     */
    final long getAndAddProducerIndex(long delta)
    {
        return UNSAFE.getAndAddLong(this, P_INDEX_OFFSET, delta);
    }
}

abstract class MpUnboundedXaddArrayQueuePad2<E> extends MpUnboundedXaddArrayQueueProducerFields<E>
{
    /**
     * 缓存行填充，保护上面的{@code producerIndex}和{@link MpUnboundedXaddArrayQueueProducerChunk}中的属性
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
}

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueProducerChunk<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueuePad2<E>
{
    private static final long P_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerChunk.class, "producerChunk");
    private static final long P_CHUNK_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueProducerChunk.class, "producerChunkIndex");

    /**
     * 最快的生产者当前填充的块，这其实是{@link MpscLinkedQueue}中的{@code producerNode}。
     * 注意：并非所有的生产者都在该块上，很可能有生产者还在填充旧的块，其它生产者通过prev获取前面的chunk。
     * 注意：这里没有producerLimit，这是一个很大的优化，它使得可以不再考虑消费者的进度（池化的chunk还是有考虑），从而减少竞争，大幅提高性能（吞吐量）。
     */
    private volatile R producerChunk;
    /**
     * 最快的生产者当前填充的块的索引（编号），每一个块都有一个编号，消费者通过该值验证{@link #producerChunk}的有效性。
     * 和{@link LinkedQueueNode}不同，{@link MpUnboundedXaddChunk}是有大小和先后关系的，即{@link MpUnboundedXaddChunk#lvNext()}。
     * 因此在CAS竞争更新{@link #producerChunk}头结点时，需要额外的标记。
     * 所以：这其实是个锁标记。
     */
    private volatile long producerChunkIndex;

    /**
     * loadVolatileProducerChunkIndex
     * 因为是多生产者模式，因此必须volatile模式读取
     */
    final long lvProducerChunkIndex()
    {
        return producerChunkIndex;
    }

    /**
     * 目前该方法仅用于扩容（申请新的块的时候使用）
     */
    final boolean casProducerChunkIndex(long expected, long value)
    {
        return UNSAFE.compareAndSwapLong(this, P_CHUNK_INDEX_OFFSET, expected, value);
    }

    /**
     * 更新使用的块的索引（编号）
     * 当其中一个生产者完成扩容时，更新索引。
     */
    final void soProducerChunkIndex(long value)
    {
        UNSAFE.putOrderedLong(this, P_CHUNK_INDEX_OFFSET, value);
    }

    /**
     * loadVolatileProducerChunk
     * 因为是多生产者模式，因此必须volatile模式读取
     */
    final R lvProducerChunk()
    {
        return this.producerChunk;
    }

    /**
     * storeOrderedProducerChunk
     * 更新当前填充的块，使用Ordered模式可以确保安全发布和尽快的可见性。
     */
    final void soProducerChunk(R chunk)
    {
        UNSAFE.putOrderedObject(this, P_CHUNK_OFFSET, chunk);
    }
}

abstract class MpUnboundedXaddArrayQueuePad3<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueueProducerChunk<R, E>
{
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
}

// $gen:ordered-fields
abstract class MpUnboundedXaddArrayQueueConsumerFields<R extends MpUnboundedXaddChunk<R, E>, E>
    extends MpUnboundedXaddArrayQueuePad3<R, E>
{
    private final static long C_INDEX_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueConsumerFields.class, "consumerIndex");
    private final static long C_CHUNK_OFFSET =
        fieldOffset(MpUnboundedXaddArrayQueueConsumerFields.class, "consumerChunk");

    /**
     * 消费者索引。
     * 消费者先根据index计算当前应该消费的chunk的索引（编号），也根据index计算落在该chunk的哪个槽位。
     */
    private volatile long consumerIndex;
    /**
     * 最快的那个消费者当前消费的chunk，这其实是{@link MpscLinkedQueue}中的{@code consumerNode}。
     */
    private volatile R consumerChunk;

    /**
     * loadVolatileConsumerIndex
     * 当不是单消费者模型下的消费者时，都应该使用该方法读取
     */
    @Override
    public final long lvConsumerIndex()
    {
        return consumerIndex;
    }

    /**
     * 更新消费者消费的块的索引（编号）
     * 当需要过渡到下一个块的时候会竞争更新索引，竞争成功的消费者，负责更新块。
     */
    final boolean casConsumerIndex(long expect, long newValue)
    {
        return UNSAFE.compareAndSwapLong(this, C_INDEX_OFFSET, expect, newValue);
    }

    /**
     * loadPlainConsumerChunk
     * 当是单消费者模型时，消费者线程可以使用该方法读取。
     */
    final R lpConsumerChunk()
    {
        return (R) UNSAFE.getObject(this, C_CHUNK_OFFSET);
    }

    /**
     * loadVolatileConsumerChunk
     * 当不是单消费者模型下的消费者时，都应该使用该方法读取
     */
    final R lvConsumerChunk()
    {
        return this.consumerChunk;
    }

    /**
     * storeOrderedConsumerChunk
     * 更新当前消费的chunk，多消费者模式下使用，需要保证对其它消费者尽快可见
     */
    final void soConsumerChunk(R newValue)
    {
        UNSAFE.putOrderedObject(this, C_CHUNK_OFFSET, newValue);
    }

    /**
     * loadPlainConsumerIndex
     * 当是单消费者模型时，消费者线程可以使用该方法读取。
     */
    final long lpConsumerIndex()
    {
        return UNSAFE.getLong(this, C_INDEX_OFFSET);
    }

    /**
     * storeOrderedConsumerIndex
     * 更新当前消费的chunk的索引（编号），需要保证原子存储 和 保证对其它消费者尽快可见。
     */
    final void soConsumerIndex(long newValue)
    {
        UNSAFE.putOrderedLong(this, C_INDEX_OFFSET, newValue);
    }
}

abstract class MpUnboundedXaddArrayQueuePad5<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueueConsumerFields<R, E>
{
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
}

/**
 * Q: 同样是无界队列，为什么该类的吞吐量远高于{@link MpscUnboundedArrayQueue}
 * A: 虽然{@link MpscUnboundedArrayQueue}也是无界队列，但它是伪无界队列，仍然考虑了消费者进度(节省空间)，因此产生了许多额外的竞争。
 * 而这里的实现更像{@link MpscLinkedQueue}，只需要处理生产者之间的竞争，不再考虑消费者进度，空间换时间，因而能大大提高吞吐量。
 * <p>
 * 此外，这里的{@link MpUnboundedXaddChunk}并不是简单的当做环形数组用的，而是在多个chunk之间循环（动态个数的chunk之间循环），
 * 把多个chunk构成了一个大的循环数组，先根据pIndex/cIndex计算使用的chunk的索引（编号），再根据index计算落在该chunk的哪个槽位。
 * 让我想起一致性hash，增加chunk和删除chunk不会影响当前的计算。
 * <p>
 * PS：最好当做{@link MpscLinkedQueue}的变种理解该类，把每一个Node换成了Chunk。
 *
 * Common infrastructure for the XADD queues.
 *
 * @author https://github.com/franz1981
 */
abstract class MpUnboundedXaddArrayQueue<R extends MpUnboundedXaddChunk<R,E>, E>
    extends MpUnboundedXaddArrayQueuePad5<R, E>
    implements MessagePassingQueue<E>, QueueProgressIndicators
{
    /**
     * 竞争更新{@link MpUnboundedXaddChunk}时使用的标记，一定不可以等于{@link MpUnboundedXaddChunk#NOT_USED}。
     * 这里强调不可以相等，那为何不选择做加减法呢？
     */
    // it must be != MpUnboundedXaddChunk.NOT_USED
    private static final long ROTATION = -2;

    /**
     * {@link MpUnboundedXaddChunk}的buffer的掩码。
     * 存储在这里可以减少存储，也可以更方便的读取。
     */
    final int chunkMask;
    /**
     * chunk的size对应的二进制表示中右边有几个0，举个栗子：1024为2^10，其右边有10个0
     * Q: 这个是干嘛的？
     * A: 除法运算优化，通过 {@code index >> chunkShift} 代替除法，计算当前使用的chunk的索引（编号）。
     * 强调：chunk并不是简单当做循环缓冲区使用的，而是先根据index计算当前使用的chunk，再根据index计算落在chunk上的哪个槽位。
     */
    final int chunkShift;
    /**
     * {@link MpUnboundedXaddChunk}的缓存池
     * <p>
     * Q: 为什么是单生产者单消费者队列？
     * A: 对于生产者而言，任意时刻至多一个线程去更新下一个填充的chunk（从池中poll），
     * 对于消费者而言也是一样，任意时刻至多一个线程去更新下一个消费的chunk（向池中offer），
     * 因此Spsc队列即可。
     * <p>
     * Q: 什么时候归还给池？
     * A: 单消费者逻辑较为简单，消费完当前块就可以归还给池。但是在多消费者下并不容易，我们无法做到消费完当前块就归还给池，
     * 因为我们无法确定当前chunk是否已经消费完毕，因为我们并不知道当前正在消费哪些槽位上的数据。
     * 因此我们只能在有一个消费者开始消费下一个chunk的时候就归还，并且生产者必须等待槽位上为null才可以填充。
     * PS: 可能导致连续的槽位，存储着属于不同环的数据。
     */
    final SpscArrayQueue<R> freeChunksPool;

    /**
     * @param chunkSize The buffer size to be used in each chunk of this queue
     * @param maxPooledChunks The maximum number of reused chunks kept around to avoid allocation, chunks are pre-allocated
     */
    MpUnboundedXaddArrayQueue(int chunkSize, int maxPooledChunks)
    {
        if (!UnsafeAccess.SUPPORTS_GET_AND_ADD_LONG)
        {
            throw new IllegalStateException("Unsafe::getAndAddLong support (JDK 8+) is required for this queue to work");
        }
        if (maxPooledChunks < 0)
        {
            throw new IllegalArgumentException("Expecting a positive maxPooledChunks, but got:"+maxPooledChunks);
        }
        chunkSize = Pow2.roundToPowerOfTwo(chunkSize);

        this.chunkMask = chunkSize - 1;
        this.chunkShift = Integer.numberOfTrailingZeros(chunkSize);
        freeChunksPool = new SpscArrayQueue<R>(maxPooledChunks);

        // Q: 为什么初始化chunkIndex的索引为0？
        // A: 看过类文档的话应该知道，因为chunkIndex是由pIndex/cIndex / chunkSize计算的，初始pIndex/cIndex为0，因此chunkIndex自然为0。

        final R first = newChunk(0, null, chunkSize, maxPooledChunks > 0);
        soProducerChunk(first);
        soProducerChunkIndex(0);
        soConsumerChunk(first);

        // 注意：只有初始化的chunk的pooled标记为true，目的是为了减少阻塞，详情见MpUnboundedXaddChunk中的注释。
        for (int i = 1; i < maxPooledChunks; i++)
        {
            freeChunksPool.offer(newChunk(NOT_USED, null, chunkSize, true));
        }
    }

    /**
     * 这是一个工厂方法，用于创建子类特定的chunk。
     */
    abstract R newChunk(long index, R prev, int chunkSize, boolean pooled);

    @Override
    public long currentProducerIndex()
    {
        return lvProducerIndex();
    }

    @Override
    public long currentConsumerIndex()
    {
        return lvConsumerIndex();
    }

    /**
     * Q: 这个方法是干嘛的？
     * A: 举个栗子，假设一个chunk的大小为8192，当前生产者申请到一个pIndex，我们假设为1，根据运算呢，它应该落在0号chunk上，但是因为是多生产者模式，
     * 因此可能另一个生产者的pIndex已经到8193了，即另一个生产者已经开始填充1号chunk了（好比你用ie浏览器，你才刷新第一个界面，别人已经刷第二个界面了），
     * 那么当前生产者读取到的chunk的index可能是0，也可能是1，如果是0，则直接填充；如果是1，则不能填充，需要调整（前后滚动）。
     * <p>
     * 我们来这里是因为当前块索引与预期的ChunkIndex不匹配。为了解决这个问题，我们现在必须将链接的块跟踪到适当的块。不止一个生产者可能会竞争添加或发现新的区块。
     *
     * We're here because currentChunk.index doesn't match the expectedChunkIndex. To resolve we must now chase the linked
     * chunks to the appropriate chunk. More than one producer may end up racing to add or discover new chunks.
     *
     * @param initialChunk the starting point chunk, which does not match the required chunk index
     * @param requiredChunkIndex the chunk index we need
     * @return the chunk matching the required index
     */
    final R producerChunkForIndex(
        final R initialChunk,
        final long requiredChunkIndex)
    {
        // 当前检查的chunk，需要和producerChunk进行检查（比较索引大小）
        R currentChunk = initialChunk;
        // 后跳步数 - 当生产者速度较快时，不同生产者可能使用不同的chunk，因此可能看见进度最快的那个生产者的chunk，因此需要后跳
        long jumpBackward;
        while (true)
        {
            if (currentChunk == null)
            {
                currentChunk = lvProducerChunk();
            }
            final long currentChunkIndex = currentChunk.lvIndex();
            assert currentChunkIndex != NOT_USED;

            // if the required chunk index is less than the current chunk index then we need to walk the linked list of
            // chunks back to the required index
            jumpBackward = currentChunkIndex - requiredChunkIndex;
            if (jumpBackward >= 0)
            {
                // 走到这，表示我等待的chunk已经被发布了，那么生产者就可以在该节点上进行填充了
                break;
            }

            // 走到这，表示当前的chunk仍然小于我期望的chunk，则当前线程可能需要尝试创建chunk并进行链接
            // try validate against the last producer chunk index
            if (lvProducerChunkIndex() == currentChunkIndex)
            {
                // 走到这，表示当前chunk是最新的chunk，但是还未满足需求，那么需要在它后面插入指定数量的chunk
                currentChunk = appendNextChunks(currentChunk, currentChunkIndex, -jumpBackward);
            }
            else
            {
                currentChunk = null;
            }
        }

        // 因为生产者的速度差异，因此可能多跳（也可以通过索引比较，所以下面有个断言）
        for (long i = 0; i < jumpBackward; i++)
        {
            // 如果chunk是新创建的，那么它的prev一定是不为null的（构造时指定并安全发布的）
            // 如果chunk是缓冲池取出的，它的prev也不为null，因为消费者不能在不使用我们试图获取块的元素的情况下使其为空？ - 尚未阅读多消费者的实现

            // prev cannot be null, because the consumer cannot null it without consuming the element for which we are
            // trying to get the chunk.
            currentChunk = currentChunk.lvPrev();
            assert currentChunk != null;
        }
        assert currentChunk.lvIndex() == requiredChunkIndex;
        return currentChunk;
    }

    /**
     * Q: 该方法是干嘛的？
     * A: 和MpscLinkedQueue不同，LinkedQueueNode之间是没有顺序的，因此生产者可以以任意顺序发布Node（头节点），
     * 但是这里的Chunk是有编号的，Chunk不能随意拼接，必须保证Chunk之间是有序的，那怎么办呢？
     * 这里的解决方案如下：
     * 生产者等待{@link #lvProducerChunkIndex()}为currentChunkIndex，然后再尝试更新，即始终等待上一个编号的chunk发布以后，当前线程再进行下一步，这样整个流程就是串行的了。
     * 举个例子：如果我想发布2号chunk，那么必须等待1号chunk发布，而想发布3号chunk的线程必须等待2号chunk发布。
     */
    private R appendNextChunks(
        R currentChunk,
        long currentChunkIndex,
        long chunksToAppend)
    {
        assert currentChunkIndex != NOT_USED;
        // prevent other concurrent attempts on appendNextChunk
        if (!casProducerChunkIndex(currentChunkIndex, ROTATION))
        {
            // CAS失败，表示有其它生产者正在尝试创建下一个chunk，因此当前线程需要等待其创建完成
            return null;
        }
        // 走到这，表示当前线程已获得更新producerChunk的权力，是与其它生产者互斥的（加锁）。
        // 在这期间，其它消费者无法在这期间增加pIndex。

        /* LOCKED FOR APPEND */
        {
            // it is valid for the currentChunk to be consumed while appending is in flight, but it's not valid for the
            // current chunk ordering to change otherwise.
            assert currentChunkIndex == currentChunk.lvIndex();

            for (long i = 1; i <= chunksToAppend; i++)
            {
                // 这里是双向列表
                // 对其它生产者而言：新的chunk一旦发布，就可以找到它的前驱节点。
                // 对于消费者而言：同MpscLinkedQueue，先发布新的chunk，再使其可达（再链接next）。
                // 此外，没创建一个chunk就发布一个，这样同样填充该chunk的生产者就可以直接填充了，而不必等待整个扩容过程完成。

                R newChunk = newOrPooledChunk(currentChunk, currentChunkIndex + i);
                soProducerChunk(newChunk);
                //link the next chunk only when finished
                currentChunk.soNext(newChunk);
                currentChunk = newChunk;
            }

            // 在创建chunk完成之后，更新chunkIndex（解锁），这样其它生产者可以继续创建chunk（如果chunk仍然不足的话）
            // release appending
            soProducerChunkIndex(currentChunkIndex + chunksToAppend);
        }
        /* UNLOCKED FOR APPEND */
        return currentChunk;
    }

    /**
     * 创建一个新的chunk或者从缓存池中取出一个chunk。
     * <p>
     * Q: （缓存池）如何保证不会访问到错误的chunk？
     * A: 1.消费者在归还chunk到池中时，需要确保所有生产者都已经离开该chunk。
     * 虽然生产者可以跳着发布，但是消费者们是竞争一个个消费的，不会跳着消费，因此消费者消费到chunk的末尾元素时（该chunk每一个元素都已填充），可确保所有生产者已离开该chunk。
     * 2.
     *
     * <p>
     * 这里和{@link #moveToNextConsumerChunk(MpUnboundedXaddChunk, MpUnboundedXaddChunk)}是实现线程安全的缓存池的关键，也是难点，
     * 主要在于如何确保不会访问到错误的chunk。
     *
     * @param prevChunk      该chunk的上一个chunk
     * @param nextChunkIndex 下一个chunk的索引（编号）
     */
    private R newOrPooledChunk(R prevChunk, long nextChunkIndex)
    {
        R newChunk = freeChunksPool.poll();
        if (newChunk != null)
        {
            // 走到这，表示是从缓存池取出的chunk
            // 单写者：读者通过 prevChunk.index == nextChunkIndex 条件保护了这里的正确性 - 没找到指的是哪里。。。
            // 由于是从池中取出的chunk，因此它的index应该小于我们正在使用的chunk

            // Q: 为什么可以使用Plain模式设置prev？
            // A: 首先，可能仍有消费者在prev上消费，但一定不会有生产者在prev上，当前chunk对于生产者而言尚不可达。
            // 接下来会安全发布chunk，这之后生产者才可达。

            // single-writer: prevChunk::index == nextChunkIndex is protecting it
            assert newChunk.lvIndex() < prevChunk.lvIndex();
            newChunk.soPrev(prevChunk);

            // 这里使用Ordered模式有两个作用：
            // 1. 实现long的原子存储
            // 2. 确保prev的可见性，其它生产者通过index确定是否可以访问prev，所以需要index来保护prev （其实接下来的chunk的发布也可以保证它）

            // index set is releasing prev, allowing other pending offers to continue
            newChunk.soIndex(nextChunkIndex);
        }
        else
        {
            // 新创建的chunk - 没有什么需要特别注意的
            newChunk = newChunk(nextChunkIndex, prevChunk, chunkMask + 1, false);
        }
        return newChunk;
    }


    /**
     * 在单消费者的情况下，消费者可以确保已消费完才归还chunk到池中，
     * 在多消费者的情况下，消费者可能提前归还chunk，因此生产者在使用池中的chunk时需要避免覆盖未消费的数据，又演化成了{@link MpmcArrayQueue}。
     * 注意：在多消费者情况下，虽然消费者可能提前归还chunk，但是该chunk一定完成了本轮的填充，即最快的消费者的进度不可以超过最慢的生产者进度。
     * 换句话说：
     * 1. 消费者一旦归还chunk到池中，生产者在从池中取出该chunk前不会引用该chunk。
     * 2. 消费者一旦开始消费next，生产者在从池中重新取出该chunk之前不会引用该chunk。
     * <p>
     * 该方法并没空出'next'的第一个元素，调用方需要自己去实现。
     * Does not null out the first element of `next`, callers must do that
     */
    final void moveToNextConsumerChunk(R cChunk, R next)
    {
        // 看代码还是少了点全局观，这里没有设置cChunk的prev为null，我以为作者忘记了，还特意问了下。
        // 是这样的，第一个节点没有prev，而next会设置prev为null，也就是第二个节点开始会清理prev，这就就可以保证cChunk的prev始终为null了。

        // avoid GC nepotism
        cChunk.soNext(null);
        next.soPrev(null);
        // no need to cChunk.soIndex(NOT_USED)
        if (cChunk.isPooled())
        {
            final boolean pooled = freeChunksPool.offer(cChunk);
            assert pooled;
        }
        this.soConsumerChunk(next);

        // 发布chunk以后，在多消费者模型下，其它消费者可以看见该chunk，也可以开始消费，也就意味着单线程阶段结束。
        // MC case:
        // from now on the code is not single-threaded anymore and
        // other consumers can move forward consumerIndex
    }

    @Override
    public Iterator<E> iterator()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size()
    {
        return IndexedQueueSizeUtil.size(this);
    }

    @Override
    public boolean isEmpty()
    {
        return IndexedQueueSizeUtil.isEmpty(this);
    }

    @Override
    public int capacity()
    {
        return MessagePassingQueue.UNBOUNDED_CAPACITY;
    }

    @Override
    public boolean relaxedOffer(E e)
    {
        return offer(e);
    }

    @Override
    public int drain(Consumer<E> c)
    {
        return MessagePassingQueueUtil.drain(this, c);
    }

    @Override
    public int fill(Supplier<E> s)
    {
        final int chunkCapacity = chunkMask + 1;
        final int offerBatch = Math.min(PortableJvmInfo.RECOMENDED_OFFER_BATCH, chunkCapacity);
        return MessagePassingQueueUtil.fillInBatchesToLimit(this, s, offerBatch, chunkCapacity);
    }

    @Override
    public int drain(Consumer<E> c, int limit)
    {
        return MessagePassingQueueUtil.drain(this, c, limit);
    }

    @Override
    public void drain(Consumer<E> c, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.drain(this, c, wait, exit);
    }

    @Override
    public void fill(Supplier<E> s, WaitStrategy wait, ExitCondition exit)
    {
        MessagePassingQueueUtil.fill(this, s, wait, exit);
    }

    @Override
    public String toString()
    {
        return this.getClass().getName();
    }
}
