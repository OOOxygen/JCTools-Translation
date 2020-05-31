package org.jctools.queues;

import org.jctools.util.InternalAPI;

import static org.jctools.util.UnsafeAccess.UNSAFE;
import static org.jctools.util.UnsafeAccess.fieldOffset;
import static org.jctools.util.UnsafeRefArrayAccess.*;

/**
 * 每一个Chunk其实都是一个{@link ConcurrentCircularArrayQueue} + {@link LinkedQueueNode}的组合。
 * <p>
 * 和{@link LinkedQueueNode}不同，{@link MpUnboundedXaddChunk}是有大小和先后关系的，即{@link #index}。
 * 和{@link ConcurrentCircularArrayQueue}不同，{@link MpUnboundedXaddChunk}并非是直接在buffer上循环，
 * 而是索引超出当前chunk则切换到下一个chunk。
 * <p>
 * 这是一个双向链表，生产者关注前驱节点{@link #prev}，消费者关注后继节点{@link #next}。
 */
@InternalAPI
class MpUnboundedXaddChunk<R,E>
{
    final static int NOT_USED = -1;

    private static final long PREV_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "prev");
    private static final long NEXT_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "next");
    private static final long INDEX_OFFSET = fieldOffset(MpUnboundedXaddChunk.class, "index");

    /**
     * 是否是缓存池中的chunk。
     * <p>
     * Q: 为什么只有初始化的那几个块的pooled标记是true，后期创建的chunk都是false？
     * A: 可以减少阻塞，如果不这样的话，生产者在填充前都必须调用{@link #spinForElement(int, boolean)}以确保该槽位可以填充，
     * 这会增加许多开销，而如果我们只对确定的chunk上调用自旋等待方法，则可以提高性能。
     */
    private final boolean pooled;
    private final E[] buffer;

    private volatile R prev;
    private volatile long index;
    private volatile R next;
    MpUnboundedXaddChunk(long index, R prev, int size, boolean pooled)
    {
        buffer = allocateRefArray(size);
        // 这里的soPred似乎是不必的，可以使用spPre赋值，因为接下来必定伴随着安全发布过程，生产者必定会安全发布该对象
        // next is null
        soPrev(prev);
        spIndex(index);
        this.pooled = pooled;
    }

    final boolean isPooled()
    {
        return pooled;
    }

    final long lvIndex()
    {
        return index;
    }

    final void soIndex(long index)
    {
        UNSAFE.putOrderedLong(this, INDEX_OFFSET, index);
    }

    final void spIndex(long index)
    {
        UNSAFE.putLong(this, INDEX_OFFSET, index);
    }

    final R lvNext()
    {
        return next;
    }

    final void soNext(R value)
    {
        UNSAFE.putOrderedObject(this, NEXT_OFFSET, value);
    }

    final R lvPrev()
    {
        return prev;
    }

    final void soPrev(R value)
    {
        UNSAFE.putObject(this, PREV_OFFSET, value);
    }

    final void soElement(int index, E e)
    {
        soRefElement(buffer, calcRefElementOffset(index), e);
    }

    final E lvElement(int index)
    {
        return lvRefElement(buffer, calcRefElementOffset(index));
    }

    final E spinForElement(int index, boolean isNull)
    {
        E[] buffer = this.buffer;
        long offset = calcRefElementOffset(index);
        E e;
        do
        {
            e = lvRefElement(buffer, offset);
        }
        while (isNull != (e == null));
        return e;
    }
}
