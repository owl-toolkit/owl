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

package owl.bdd;

import com.google.common.collect.Maps;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;

/**
 * A multi-terminal binary decision diagram (MTBDD).
 *
 * <p>This class provides an implementation of ordered, but not necessarily reduced, MTBDDs.
 *
 * @param <E> the elements stored at the leaves of the MTBDD.
 */
public abstract class MtBdd<E> {

  private MtBdd() {}

  @SuppressWarnings("unchecked")
  public static <E> MtBdd<E> of() {
    return (MtBdd<E>) Leaf.EMPTY;
  }

  public static <E> MtBdd<E> of(Collection<? extends E> value) {
    Set<E> set = Set.copyOf(value);
    return set.isEmpty() ? of() : new Leaf<>(set);
  }

  public static <E> MtBdd<E> of(int variable, MtBdd<E> trueChild, MtBdd<E> falseChild) {
    if (trueChild.equals(falseChild)) {
      return trueChild;
    }

    return new Node<>(variable, trueChild, falseChild);
  }

  public abstract Set<E> get(BitSet valuation);

  public final Set<E> flatValues() {
    Set<E> values = new HashSet<>();
    memoizedFlatValues(values, Collections.newSetFromMap(new IdentityHashMap<>()));
    return values;
  }

  public final Set<Set<E>> values() {
    Set<Set<E>> values = new HashSet<>();
    memoizedValues(values, Collections.newSetFromMap(new IdentityHashMap<>()));
    return values;
  }

  public final Map<E, BddSet> inverse(BddSetFactory factory) {
    return inverse(factory, IntUnaryOperator.identity());
  }

  public final Map<E, BddSet> inverse(BddSetFactory factory, IntUnaryOperator mapping) {
    return memoizedInverse(factory, new HashMap<>(), mapping);
  }

  public final <T> MtBdd<T> map(
    Function<? super Set<E>, ? extends Collection<? extends T>> mapper) {
    return memoizedMap(mapper, new HashMap<>());
  }

  protected abstract <T> MtBdd<T> memoizedMap(
    Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
    Map<MtBdd<E>, MtBdd<T>> memoizedCalls);

  protected abstract Map<E, BddSet> memoizedInverse(
    BddSetFactory factory,
    Map<MtBdd<E>, Map<E, BddSet>> memoizedCalls,
    IntUnaryOperator mapping);

  protected abstract void memoizedFlatValues(Set<E> values, Set<MtBdd<E>> seenNodes);

  protected abstract void memoizedValues(
    Set<Set<E>> values, Set<MtBdd<E>> seenNodes);

  public static final class Leaf<E> extends MtBdd<E> {
    private static final MtBdd<?> EMPTY = new Leaf<>(Set.of());

    public final Set<E> value;

    private Leaf(Set<E> value) {
      assert value == Set.copyOf(value);
      this.value = value;
    }

    @Override
    public Set<E> get(BitSet valuation) {
      return value;
    }

    @Override
    protected void memoizedFlatValues(Set<E> values, Set<MtBdd<E>> seenNodes) {
      values.addAll(value);
    }

    @Override
    protected void memoizedValues(Set<Set<E>> values, Set<MtBdd<E>> seenNodes) {
      values.add(value);
    }

    @Override
    protected <T> MtBdd<T> memoizedMap(
      Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
      Map<MtBdd<E>, MtBdd<T>> memoizedCalls) {

      return memoizedCalls.computeIfAbsent(this, x -> of(mapper.apply(value)));
    }

    @Override
    protected Map<E, BddSet> memoizedInverse(
      BddSetFactory factory,
      Map<MtBdd<E>, Map<E, BddSet>> memoizedCalls,
      IntUnaryOperator mapping) {

      return memoizedCalls.computeIfAbsent(this, x -> Maps.asMap(value, y -> factory.of(true)));
    }

    @Override
    public boolean equals(Object o) {
      return this == o || (o instanceof Leaf && value.equals(((Leaf<?>) o).value));
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "(" + value + ')';
    }
  }

  public static final class Node<E> extends MtBdd<E> {
    public final int variable;
    public final MtBdd<E> trueChild;
    public final MtBdd<E> falseChild;
    private final int hashCode;

    private Node(int variable, MtBdd<E> trueChild, MtBdd<E> falseChild) {
      if (variable < 0) {
        throw new IndexOutOfBoundsException(variable);
      }

      if (trueChild instanceof Node) {
        Objects.checkIndex(variable, ((Node<E>) trueChild).variable);
      }

      if (falseChild instanceof Node) {
        Objects.checkIndex(variable, ((Node<E>) falseChild).variable);
      }

      this.variable = variable;
      this.trueChild = trueChild;
      this.falseChild = falseChild;
      this.hashCode = Objects.hash(variable, trueChild, falseChild);
    }

    @Override
    public Set<E> get(BitSet valuation) {
      return valuation.get(variable) ? trueChild.get(valuation) : falseChild.get(valuation);
    }

    @Override
    protected void memoizedFlatValues(Set<E> values, Set<MtBdd<E>> seenNodes) {
      if (!seenNodes.add(this)) {
        return;
      }

      trueChild.memoizedFlatValues(values, seenNodes);
      falseChild.memoizedFlatValues(values, seenNodes);
    }

    @Override
    protected void memoizedValues(
      Set<Set<E>> values,
      Set<MtBdd<E>> seenNodes) {

      if (!seenNodes.add(this)) {
        return;
      }

      trueChild.memoizedValues(values, seenNodes);
      falseChild.memoizedValues(values, seenNodes);
    }

    // Perfect for fork/join-parallesism
    @Override
    protected <T> MtBdd<T> memoizedMap(
      Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
      Map<MtBdd<E>, MtBdd<T>> memoizedCalls) {

      MtBdd<T> mappedNode = memoizedCalls.get(this);

      if (mappedNode != null) {
        return mappedNode;
      }

      mappedNode = of(variable,
        trueChild.memoizedMap(mapper, memoizedCalls),
        falseChild.memoizedMap(mapper, memoizedCalls));
      memoizedCalls.put(this, mappedNode);
      return mappedNode;
    }

    @Override
    protected Map<E, BddSet> memoizedInverse(
      BddSetFactory factory,
      Map<MtBdd<E>, Map<E, BddSet>> memoizedCalls,
      IntUnaryOperator mapping) {

      Map<E, BddSet> map = memoizedCalls.get(this);

      if (map != null) {
        return map;
      }

      var trueMask = factory.of(mapping.applyAsInt(variable));
      var falseMask = trueMask.complement();
      var newMap = new HashMap<E, BddSet>();

      trueChild.memoizedInverse(factory, memoizedCalls, mapping).forEach(
        (key, set) -> newMap.merge(key, set.intersection(trueMask), BddSet::union));
      falseChild.memoizedInverse(factory, memoizedCalls, mapping).forEach(
        (key, set) -> newMap.merge(key, set.intersection(falseMask), BddSet::union));

      memoizedCalls.put(this, newMap);
      return newMap;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof Node)) {
        return false;
      }

      Node<?> that = (Node<?>) o;
      return hashCode == that.hashCode
        && variable == that.variable
        && trueChild.equals(that.trueChild)
        && falseChild.equals(that.falseChild);
    }

    @Override
    public int hashCode() {
      return hashCode;
    }

    @Override
    public String toString() {
      return String.format("(V: %d, tt: %s, ff: %s)", variable, trueChild, falseChild);
    }
  }
}
