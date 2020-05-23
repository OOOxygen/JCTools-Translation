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
package org.jctools.util;

import sun.misc.Unsafe;

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * 封装对{@code long[]}数组的访问 - 主要为：（各种模式）存取元素，计算索引对应的地址偏移量。
 */
@InternalAPI
public final class UnsafeLongArrayAccess
{
    /**
     * long[]类型数组首元素（相对于对象起始地址）的地址偏移量
     */
    public static final long LONG_ARRAY_BASE;
    /**
     * 每个元素的地址偏移量对应的位移量，主要用于位运算代替乘法运算
     */
    public static final int LONG_ELEMENT_SHIFT;

    static
    {
        // 每个元素的地址偏移量
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(long[].class);
        if (8 == scale)
        {
            LONG_ELEMENT_SHIFT = 3;
        }
        else
        {
            throw new IllegalStateException("Unknown pointer size: " + scale);
        }
        LONG_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(long[].class);
    }

    /**
     * storePlainLongElement - 以普通（无顺序/无内存屏障）方式将元素写入到给定数组的给定偏移量。
     * <p>
     * A plain store (no ordering/fences) of an element to a given offset
     *
     * @param buffer le buffer
     * @param offset computed via {@link UnsafeLongArrayAccess#calcLongElementOffset(long)}
     * @param e      an orderly kitty
     */
    public static void spLongElement(long[] buffer, long offset, long e)
    {
        UNSAFE.putLong(buffer, offset, e);
    }

    /**
     * storeOrderedLongElement - 以Ordered方式将元素写入到给定数组的给定偏移量。
     * <p>
     * Q: 内存屏障插在了哪里？
     * A: 该调用等价于{@link Unsafe#storeFence()} + {@link #spLongElement(long[], long, long)}的快捷调用，即屏障插在了写入该元素指令之前，是为了保护前面的读写操作。
     * <p>
     * An ordered store of an element to a given offset
     *
     * @param buffer le buffer
     * @param offset computed via {@link UnsafeLongArrayAccess#calcCircularLongElementOffset}
     * @param e      an orderly kitty
     */
    public static void soLongElement(long[] buffer, long offset, long e)
    {
        UNSAFE.putOrderedLong(buffer, offset, e);
    }

    /**
     * loadPlainLongElement - 以普通（无序/无内存屏障）方式加载数组指定偏移量的值。
     * <p>
     * A plain load (no ordering/fences) of an element from a given offset.
     *
     * @param buffer le buffer
     * @param offset computed via {@link UnsafeLongArrayAccess#calcLongElementOffset(long)}
     * @return the element at the offset
     */
    public static long lpLongElement(long[] buffer, long offset)
    {
        return UNSAFE.getLong(buffer, offset);
    }

    /**
     * loadVolatileLongElement - 以volatile方式加载数组指定偏移量的值。
     * <p>
     * Q: 该内存屏障插在了哪里？
     * A: 该调用等价于 {@link #lpLongElement(long[], long)} + {@link Unsafe#loadFence()}，即屏障插在了读取元素指令之后，是为了保护后面的读写操作。
     * 拓展: {@link Unsafe#storeFence()}和{@link Unsafe#loadFence()}的组合使用构建了一个偏序关系，通过该偏序关系我们可以推测某些数据的可见性。
     * <p>
     * A volatile load of an element from a given offset.
     *
     * @param buffer le buffer
     * @param offset computed via {@link UnsafeLongArrayAccess#calcCircularLongElementOffset}
     * @return the element at the offset
     */
    public static long lvLongElement(long[] buffer, long offset)
    {
        return UNSAFE.getLongVolatile(buffer, offset);
    }

    /**
     * 计算普通数组的指定索引对应的偏移量 - index即为真实索引，因此简单的运算即可。
     *
     * @param index desirable element index
     * @return the offset in bytes within the array for a given index
     */
    public static long calcLongElementOffset(long index)
    {
        return LONG_ARRAY_BASE + (index << LONG_ELEMENT_SHIFT);
    }

    /**
     * 计算环形数组的指定（逻辑）索引对应的偏移量。
     * <P>
     * Q: 与普通数组计算元素偏移量的区别？<br>
     * A: 环形数组(环形缓冲区)的空间是重复利用的，因此逻辑上的index需要转换为真正的index然后再计算。
     * 为了高效运算，假定了环形数组的长度都为2的整次幂，因此mask应该为数组长度减1，这样可以使用 '&' 快速计算。
     * <P>
     * Note: circular arrays are assumed a power of 2 in length and the `mask` is (length - 1).
     *
     * @param index desirable element index
     * @param mask (length - 1)
     * @return the offset in bytes within the circular array for a given index
     */
    public static long calcCircularLongElementOffset(long index, long mask)
    {
        return LONG_ARRAY_BASE + ((index & mask) << LONG_ELEMENT_SHIFT);
    }

    /**
     * 这样可以更轻松地生成基于{@code Atomic}队列，并消除一些警告 - JCTools会在构建时生成许多基于{@code Atomic}的实现。
     * <p>
     * This makes for an easier time generating the atomic queues, and removes some warnings.
     */
    public static long[] allocateLongArray(int capacity)
    {
        return new long[capacity];
    }
}
