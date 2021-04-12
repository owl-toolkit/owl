/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package owl.collections;

import com.google.common.base.Preconditions;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import com.google.common.collect.Iterators;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * This class implements an immutable vector of bits. Each component of the bit set has a
 * {@code boolean} value. The bits of a {@code ImmutableBitSet} are indexed by nonnegative integers.
 * This class further implements the {@code Set} and provide methods to access integer
 * values without boxing.
 *
 * <p>This is a simple implementation backed by either singleton values or a BitSet. Thus using
 * large indices increases the allocated memory, since the backing BitSet does not have sparse
 * representation. The {@code ImmutableBitSet} instances have the following characteristics:
 *
 * <ul>
 * <li>They are <a href="Collection.html#unmodifiable"><i>unmodifiable</i></a>. Elements cannot
 * be added or removed. Calling any mutator method on the Set
 * will always cause {@code UnsupportedOperationException} to be thrown.
 * <li>They disallow {@code null} elements. Attempts to create them with
 * {@code null} elements result in {@code NullPointerException}.
 * <li>They reject duplicate elements at creation time. Duplicate elements
 * passed to a static factory method result in {@code IllegalArgumentException}.
 * <li>They are value-based.
 * Callers should make no assumptions about the identity of the returned instances.
 * Factories are free to create new instances or reuse existing ones. Therefore,
 * identity-sensitive operations on these instances (reference equality ({@code ==}),
 * identity hash code, and synchronization) are unreliable and should be avoided.
 * </ul>
 */
// TODO: implement SortedSet, NavigableSet
// TODO: add ImmutableBitSet.Builder
public abstract class ImmutableBitSet extends AbstractSet<Integer>
  implements Comparable<ImmutableBitSet> {

  private static final Interner<Small> SMALL_INTERNER = Interners.newWeakInterner();
  private static final Interner<Large> LARGE_INTERNER = Interners.newWeakInterner();

  private ImmutableBitSet() {}

  public static ImmutableBitSet of() {
    return Small.EMPTY;
  }

  public static ImmutableBitSet of(int element) {
    Preconditions.checkArgument(element >= 0);
    return SMALL_INTERNER.intern(new Small(element));
  }

  public static ImmutableBitSet of(int... elements) {
    BitSet builder = new BitSet();

    for (int element : elements) {
      Preconditions.checkArgument(!builder.get(element), "duplicate element: " + element);
      builder.set(element);
    }

    return LARGE_INTERNER.intern(new Large(builder));
  }

  public static ImmutableBitSet copyOf(BitSet bitSet) {
    switch (bitSet.cardinality()) {
      case 0:
        return of();

      case 1:
        return of(bitSet.nextSetBit(0));

      default:
        BitSet copy;

        if (bitSet.getClass() == BitSet.class) {
          copy = (BitSet) bitSet.clone();
        } else {
          // Manual copy, because the passed bitSet was subclassed and might do wonky stuff
          // in clone().
          copy = new BitSet();
          copy.or(bitSet);
        }

        return LARGE_INTERNER.intern(new Large(copy));
    }
  }

  public static ImmutableBitSet copyOf(Collection<Integer> collection) {
    if (collection instanceof ImmutableBitSet) {
      return (ImmutableBitSet) collection;
    }

    Iterator<Integer> iterator = collection.iterator();

    if (!iterator.hasNext()) {
      return of();
    }

    int firstColour = iterator.next();

    if (!iterator.hasNext()) {
      return of(firstColour);
    }

    BitSet colours = new BitSet();
    colours.set(firstColour);
    iterator.forEachRemaining(colours::set);

    if (colours.cardinality() == 1) {
      return of(firstColour);
    }

    return LARGE_INTERNER.intern(new Large(colours));
  }

  public abstract OptionalInt first();

  public abstract OptionalInt last();

  /**
   * Returns the least element in this set strictly greater than the
   * given element.
   *
   * @param e the value to match
   * @return the least element greater than {@code e}.
   * @throws NoSuchElementException if there is no such element.
   */
  public abstract OptionalInt higher(int e);

  /**
   * Returns the greatest element in this set strictly less than the
   * given element.
   *
   * @param e the value to match
   * @return the greatest element less than {@code e}.
   * @throws NoSuchElementException if there is no such element.
   */
  public abstract OptionalInt lower(int e);

  @Override
  public abstract boolean isEmpty();

  public abstract boolean contains(int colour);

  @Override
  public final boolean contains(Object o) {
    if (o instanceof Integer) {
      int element = (Integer) o;
      return contains(element);
    }

    return false;
  }

  public abstract boolean containsAll(BitSet colours);

  @Override
  public final boolean containsAll(Collection<?> c) {
    if (c instanceof ImmutableBitSet.Small) {
      return c.isEmpty() || contains(((Small) c).element);
    }

    if (c instanceof ImmutableBitSet.Large) {
      return containsAll(((Large) c).elements);
    }

    return super.containsAll(c);
  }

  @Override
  public abstract Stream<Integer> stream();

  public abstract IntStream intStream();

  public abstract PrimitiveIterator.OfInt intIterator();

  @Override
  public abstract void forEach(Consumer<? super Integer> action);

  public abstract void forEach(IntConsumer action);

  public abstract BitSet copyInto(BitSet target);

  public final ImmutableBitSet union(ImmutableBitSet that) {
    if (this.containsAll(that)) {
      return this;
    }

    if (that.containsAll(this)) {
      return that;
    }

    // TODO: case distinction: Small and Large Sets.
    BitSet union = this.copyInto(new BitSet());
    that.copyInto(union);

    if (union.cardinality() > 1) {
      return LARGE_INTERNER.intern(new Large(union));
    }

    return copyOf(union);
  }

  public final ImmutableBitSet intersection(ImmutableBitSet that) {
    if (this.containsAll(that)) {
      return that;
    }

    if (that.containsAll(this)) {
      return this;
    }

    // TODO: case distinction: Small and Large Sets.
    BitSet intersection = this.copyInto(new BitSet());
    intersection.and(that.copyInto(new BitSet()));

    if (intersection.cardinality() > 1) {
      return LARGE_INTERNER.intern(new Large(intersection));
    }

    return copyOf(intersection);
  }

  private static final class Small extends ImmutableBitSet {
    private static ImmutableBitSet EMPTY = new Small(Small.EMPTY_ELEMENT_VALUE);

    private static final int EMPTY_ELEMENT_VALUE = -1;

    private final int element;

    private Small(int element) {
      assert -1 <= element;
      this.element = element;
    }

    @Override
    public Iterator<Integer> iterator() {
      return isEmpty() ? Collections.emptyIterator() : Iterators.singletonIterator(element);
    }

    @Override
    public boolean isEmpty() {
      return element == EMPTY_ELEMENT_VALUE;
    }

    @Override
    public int size() {
      return isEmpty() ? 0 : 1;
    }

    @Override
    public OptionalInt first() {
      if (isEmpty()) {
        return OptionalInt.empty();
      }

      return OptionalInt.of(element);
    }

    @Override
    public OptionalInt last() {
      if (isEmpty()) {
        return OptionalInt.empty();
      }

      return OptionalInt.of(element);
    }

    @Override
    public OptionalInt higher(int e) {
      if (isEmpty() || e >= element) {
        return OptionalInt.empty();
      }

      return OptionalInt.of(element);
    }

    @Override
    public OptionalInt lower(int e) {
      if (isEmpty() || e <= element) {
        return OptionalInt.empty();
      }

      return OptionalInt.of(element);
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof Set)) {
        return false;
      }

      if (o instanceof ImmutableBitSet.Large) {
        return false;
      }

      if (o instanceof ImmutableBitSet.Small) {
        return element == ((Small) o).element;
      }

      return super.equals(o);
    }

    @Override
    public int hashCode() {
      return isEmpty() ? 0 : Integer.hashCode(element);
    }

    @Override
    public boolean contains(int element) {
      return element >= 0 && element == this.element;
    }

    @Override
    public boolean containsAll(BitSet colours) {
      return colours.isEmpty()
        || (colours.cardinality() == 1 && contains(colours.nextSetBit(0)));
    }

    @Override
    public Stream<Integer> stream() {
      return isEmpty() ? Stream.empty() : Stream.of(element);
    }

    @Override
    public IntStream intStream() {
      return isEmpty() ? IntStream.empty() : IntStream.of(element);
    }

    @Override
    public PrimitiveIterator.OfInt intIterator() {
      return intStream().iterator();
    }

    @Override
    public void forEach(Consumer<? super Integer> action) {
      if (!isEmpty()) {
        action.accept(element);
      }
    }

    @Override
    public void forEach(IntConsumer action) {
      if (!isEmpty()) {
        action.accept(element);
      }
    }

    @Override
    public BitSet copyInto(BitSet target) {
      if (!isEmpty()) {
        target.set(element);
      }

      return target;
    }

    @Override
    public int compareTo(ImmutableBitSet o) {
      if (o instanceof Large) {
        return -1;
      }

      assert o instanceof Small;
      return Integer.compare(this.element, ((Small) o).element);
    }
  }

  private static final class Large extends ImmutableBitSet {
    private final BitSet elements;

    private Large(BitSet elements) {
      Preconditions.checkArgument(elements.cardinality() > 1);
      this.elements = Objects.requireNonNull(elements);
    }

    @Override
    public OptionalInt first() {
      return OptionalInt.of(elements.nextSetBit(0));
    }

    @Override
    public OptionalInt last() {
      return OptionalInt.of(elements.length() - 1);
    }

    @Override
    public OptionalInt higher(int e) {
      int nextElement = elements.nextSetBit(Math.max(0, e + 1));
      return nextElement >= 0
        ? OptionalInt.of(nextElement)
        : OptionalInt.empty();
    }

    @Override
    public OptionalInt lower(int e) {
      int previousElement = elements.previousSetBit(Math.min(-1, e - 1));
      return previousElement >= 0
        ? OptionalInt.of(previousElement)
        : OptionalInt.empty();
    }

    @Override
    public Iterator<Integer> iterator() {
      return elements.stream().boxed().iterator();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public int size() {
      return elements.cardinality();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof Set)) {
        return false;
      }

      if (o instanceof ImmutableBitSet.Small) {
        return false;
      }

      if (o instanceof ImmutableBitSet.Large) {
        return elements.equals(((Large) o).elements);
      }

      return super.equals(o);
    }

    @Override
    public int hashCode() {
      int h = 0;

      for (int i = elements.nextSetBit(0); i >= 0; i = elements.nextSetBit(i + 1)) {
        h += Integer.hashCode(i);

        // operate on index i here
        if (i == Integer.MAX_VALUE) {
          throw new IllegalStateException("Set to large");
        }
      }

      return h;
    }

    @Override
    public boolean contains(int element) {
      return element >= 0 && elements.get(element);
    }

    @Override
    public boolean containsAll(BitSet colours) {
      BitSet copy = BitSet2.copyOf(colours);
      copy.andNot(this.elements);
      return copy.isEmpty();
    }

    @Override
    public Stream<Integer> stream() {
      return intStream().boxed();
    }

    @Override
    public IntStream intStream() {
      return elements.stream();
    }

    @Override
    public PrimitiveIterator.OfInt intIterator() {
      return intStream().iterator();
    }

    @Override
    public void forEach(Consumer<? super Integer> action) {
      elements.stream().boxed().forEach(action);
    }

    @Override
    public void forEach(IntConsumer action) {
      intStream().forEach(action);
    }

    @Override
    public BitSet copyInto(BitSet target) {
      // It is safe to use target without worrying about target changing our internal state.
      if (BitSet.class.equals(target.getClass())) {
        target.or(elements);
      } else {
        // safe-fallback.
        elements.stream().forEach(target::set);
      }

      return target;
    }

    @Override
    public int compareTo(ImmutableBitSet o) {
      if (o instanceof Small) {
        return 1;
      }

      assert o instanceof Large;
      int sizeComparison = Integer.compare(this.size(), o.size());

      if (sizeComparison != 0) {
        return sizeComparison;
      }

      return Arrays.compare(elements.stream().toArray(), ((Large) o).elements.stream().toArray());
    }
  }
}
