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

import static org.jctools.util.UnsafeAccess.UNSAFE;

/**
 * 引用类型数组的访问封装 - （各种模式）存取元素，计算索引对应的地址偏移量。
 * - 该类中部分设计不会再重复解释，可参照{@link UnsafeLongArrayAccess}进行理解。
 */
@InternalAPI
public final class UnsafeRefArrayAccess
{
    /**
     * 引用类型数组首元素（相对于对象起始地址）的偏移量
     */
    public static final long REF_ARRAY_BASE;
    /**
     * 每个引用类型偏移量对应的位移量 - 用于代替乘法运算
     */
    public static final int REF_ELEMENT_SHIFT;

    static
    {
        // 引用类型单个元素偏移量
        final int scale = UnsafeAccess.UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale)
        {
            REF_ELEMENT_SHIFT = 2;
        }
        else if (8 == scale)
        {
            REF_ELEMENT_SHIFT = 3;
        }
        else
        {
            throw new IllegalStateException("Unknown pointer size: " + scale);
        }
        REF_ARRAY_BASE = UnsafeAccess.UNSAFE.arrayBaseOffset(Object[].class);
    }

    /**
     * storePlainReferenceElement - 以普通（无顺序/无内存屏障）方式将元素写入到给定数组的给定偏移量。
     * <p>
     * A plain store (no ordering/fences) of an element to a given offset
     *
     * @param buffer this.buffer
     * @param offset computed via {@link UnsafeRefArrayAccess#calcRefElementOffset(long)}
     * @param e      an orderly kitty
     */
    public static <E> void spRefElement(E[] buffer, long offset, E e)
    {
        UNSAFE.putObject(buffer, offset, e);
    }

    /**
     * storeOrderedReferenceElement - 以Ordered方式将元素写入到给定数组的给定偏移量
     * <p>
     * An ordered store of an element to a given offset
     *
     * @param buffer this.buffer
     * @param offset computed via {@link UnsafeRefArrayAccess#calcCircularRefElementOffset}
     * @param e      an orderly kitty
     */
    public static <E> void soRefElement(E[] buffer, long offset, E e)
    {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    /**
     * loadPlainReferenceElement - 以普通（无序/无内存屏障）方式加载数组指定偏移量的值。
     * <p>
     * A plain load (no ordering/fences) of an element from a given offset.
     *
     * @param buffer this.buffer
     * @param offset computed via {@link UnsafeRefArrayAccess#calcRefElementOffset(long)}
     * @return the element at the offset
     */
    @SuppressWarnings("unchecked")
    public static <E> E lpRefElement(E[] buffer, long offset)
    {
        return (E) UNSAFE.getObject(buffer, offset);
    }

    /**
     * loadVolatileReferenceElement - 以volatile方式加载数组指定偏移量的值。
     * <p>
     * A volatile load of an element from a given offset.
     *
     * @param buffer this.buffer
     * @param offset computed via {@link UnsafeRefArrayAccess#calcRefElementOffset(long)}
     * @return the element at the offset
     */
    @SuppressWarnings("unchecked")
    public static <E> E lvRefElement(E[] buffer, long offset)
    {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }

    /**
     * 计算普通数组的指定索引对应的偏移量 - index即为真实索引，因此简单的运算即可。
     *
     * @param index desirable element index
     * @return the offset in bytes within the array for a given index
     */
    public static long calcRefElementOffset(long index)
    {
        return REF_ARRAY_BASE + (index << REF_ELEMENT_SHIFT);
    }

    /**
     * 计算环形数组的指定（逻辑）索引对应的偏移量 - index为逻辑索引，需要转换为真实索引。
     * <p>
     * 环形数组(环形缓冲区)的空间是重复利用的，因此逻辑上的index需要转换为真正的index然后再计算。
     * 为了高效运算，假定了环形数组的长度都为2的整次幂，因此mask应该为数组长度减1，这样可以使用 '&' 快速计算。
     *
     * Note: circular arrays are assumed a power of 2 in length and the `mask` is (length - 1).
     *
     * @param index desirable element index
     * @param mask (length - 1)
     * @return the offset in bytes within the circular array for a given index
     */
    public static long calcCircularRefElementOffset(long index, long mask)
    {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    /**
     * This makes for an easier time generating the atomic queues, and removes some warnings.
     */
    @SuppressWarnings("unchecked")
    public static <E> E[] allocateRefArray(int capacity)
    {
        return (E[]) new Object[capacity];
    }
}
