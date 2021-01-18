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

package owl.automaton.edge;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import owl.collections.BitSet2;

// TODO: implement SortedSet<Integer>; override all mutators with UOE; Cache small instances.
public abstract class Colours extends AbstractSet<Integer> implements Comparable<Colours> {

  // Hide constructor.
  private Colours() {}

  public static Colours of() {
    return Small.EMPTY;
  }

  public static Colours of(int colour) {
    Preconditions.checkArgument(colour >= 0);
    return new Small(colour);
  }

  public static Colours of(int firstColour, int... moreColours) {
    BitSet colours = new BitSet();
    colours.set(firstColour);

    for (int colour : moreColours) {
      Preconditions.checkArgument(!colours.get(colour));
      colours.set(colour);
    }

    return new Large(colours);
  }

  public static Colours copyOf(BitSet colours) {
    switch (colours.cardinality()) {
      case 0:
        return of();

      case 1:
        return of(colours.nextSetBit(0));

      default:
        BitSet colourCopy = new BitSet();
        colourCopy.or(colours);
        return new Large(colourCopy);
    }
  }

  public static Colours copyOf(Collection<Integer> colourCollection) {
    if (colourCollection instanceof Colours) {
      return (Colours) colourCollection;
    }

    Iterator<Integer> iterator = colourCollection.iterator();

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

    return new Large(colours);
  }

  public abstract int first();

  public abstract int last();

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
    if (c instanceof Colours.Small) {
      return c.isEmpty() || contains(((Small) c).element);
    }

    if (c instanceof Colours.Large) {
      return containsAll(((Large) c).colours);
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

  public abstract BitSet asBitSet();

  public final Colours intersection(Colours that) {
    if (this.containsAll(that)) {
      return that;
    }

    if (that.containsAll(this)) {
      return this;
    }

    BitSet intersection = this.asBitSet();
    intersection.and(that.asBitSet());

    if (intersection.cardinality() > 1) {
      return new Large(intersection);
    }

    return copyOf(intersection);
  }

  private static final class Small extends Colours {
    private static Colours EMPTY = new Small(Small.EMPTY_ELEMENT_VALUE);

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
    public int first() {
      if (isEmpty()) {
        return Integer.MAX_VALUE;
      }

      return element;
    }

    @Override
    public int last() {
      if (isEmpty()) {
        return -1;
      }

      return element;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof Set)) {
        return false;
      }

      if (o instanceof Colours.Large) {
        return false;
      }

      if (o instanceof Colours.Small) {
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
    public BitSet asBitSet() {
      BitSet bitSet = new BitSet();

      if (!isEmpty()) {
        bitSet.set(element);
      }

      return bitSet;
    }

    @Override
    public int compareTo(Colours o) {
      if (o instanceof Large) {
        return -1;
      }

      assert o instanceof Small;
      return Integer.compare(this.element, ((Small) o).element);
    }
  }

  private static final class Large extends Colours {
    private final BitSet colours;

    private Large(BitSet colours) {
      assert colours.cardinality() > 1;
      this.colours = Objects.requireNonNull(colours);
    }

    @Override
    public int first() {
      return colours.nextSetBit(0);
    }

    @Override
    public int last() {
      return colours.length() - 1;
    }

    @Override
    public Iterator<Integer> iterator() {
      return colours.stream().boxed().iterator();
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public int size() {
      return colours.cardinality();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof Set)) {
        return false;
      }

      if (o instanceof Colours.Small) {
        return false;
      }

      if (o instanceof Colours.Large) {
        return colours.equals(((Large) o).colours);
      }

      return super.equals(o);
    }

    @Override
    public int hashCode() {
      int h = 0;

      for (int i = colours.nextSetBit(0); i >= 0; i = colours.nextSetBit(i + 1)) {
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
      return element >= 0 && colours.get(element);
    }

    @Override
    public boolean containsAll(BitSet colours) {
      BitSet copy = BitSet2.copyOf(colours);
      copy.andNot(this.colours);
      return copy.isEmpty();
    }

    @Override
    public Stream<Integer> stream() {
      return intStream().boxed();
    }

    @Override
    public IntStream intStream() {
      return colours.stream();
    }

    @Override
    public PrimitiveIterator.OfInt intIterator() {
      return intStream().iterator();
    }

    @Override
    public void forEach(Consumer<? super Integer> action) {
      colours.stream().boxed().forEach(action);
    }

    @Override
    public void forEach(IntConsumer action) {
      intStream().forEach(action);
    }

    @Override
    public BitSet asBitSet() {
      return (BitSet) colours.clone();
    }

    @Override
    public int compareTo(Colours o) {
      if (o instanceof Small) {
        return 1;
      }

      assert o instanceof Large;
      int sizeComparison = Integer.compare(this.size(), o.size());

      if (sizeComparison != 0) {
        return sizeComparison;
      }

      return Arrays.compare(colours.stream().toArray(), ((Large) o).colours.stream().toArray());
    }
  }
}
