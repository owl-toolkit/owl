/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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
import owl.factories.ValuationSetFactory;

public abstract class ValuationTree<E> {
  private ValuationTree() {
  }

  public static <E> ValuationTree<E> of() {
    return (ValuationTree<E>) Leaf.EMPTY;
  }

  public static <E> ValuationTree<E> of(Collection<? extends E> value) {
    Set<E> set = Set.copyOf(value);
    return set.isEmpty() ? of() : new Leaf<>(set);
  }

  public static <E> ValuationTree<E> of(int variable, ValuationTree<E> trueChild,
    ValuationTree<E> falseChild) {
    if (trueChild.equals(falseChild)) {
      return trueChild;
    }

    return new Node<>(variable, trueChild, falseChild);
  }

  public abstract Set<E> get(BitSet valuation);

  public final Set<E> values() {
    return values(Function.identity());
  }

  public final <T> Set<T> values(Function<E, T> mapper) {
    Set<T> values = new HashSet<>();
    memoizedValues(values, Collections.newSetFromMap(new IdentityHashMap<>()), mapper);
    return values;
  }

  public final Map<E, ValuationSet> inverse(ValuationSetFactory factory) {
    return memoizedInverse(factory, new HashMap<>());
  }

  public final <T> ValuationTree<T> map(
    Function<? super Set<E>, ? extends Collection<? extends T>> mapper) {
    return memoizedMap(mapper, new HashMap<>());
  }

  protected abstract <T> ValuationTree<T> memoizedMap(
    Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
    Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls);

  protected abstract Map<E, ValuationSet> memoizedInverse(
    ValuationSetFactory factory,
    Map<ValuationTree<E>, Map<E, ValuationSet>> memoizedCalls);

  protected abstract <T> void memoizedValues(
    Set<T> values, Set<ValuationTree<E>> seenNodes, Function<E, T> mapper);

  public static final class Leaf<E> extends ValuationTree<E> {
    private static final ValuationTree<Object> EMPTY = new Leaf<>(Set.of());

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
    protected <T> void memoizedValues(Set<T> values,
      Set<ValuationTree<E>> seenNodes, Function<E, T> mapper) {
      for (E x : value) {
        values.add(mapper.apply(x));
      }
    }

    @Override
    protected <T> ValuationTree<T> memoizedMap(
      Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
      Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls) {
      return memoizedCalls.computeIfAbsent(this, x -> of(mapper.apply(value)));
    }

    @Override
    protected Map<E, ValuationSet> memoizedInverse(ValuationSetFactory factory,
      Map<ValuationTree<E>, Map<E, ValuationSet>> memoizedCalls) {
      return memoizedCalls.computeIfAbsent(this, x -> Maps.asMap(value, y -> factory.universe()));
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

  public static final class Node<E> extends ValuationTree<E> {
    public final int variable;
    public final ValuationTree<E> trueChild;
    public final ValuationTree<E> falseChild;
    private final int hashCode;

    private Node(int variable, ValuationTree<E> trueChild, ValuationTree<E> falseChild) {
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
    protected <T> void memoizedValues(Set<T> values, Set<ValuationTree<E>> seenNodes,
      Function<E, T> mapper) {
      if (!seenNodes.add(this)) {
        return;
      }

      trueChild.memoizedValues(values, seenNodes, mapper);
      falseChild.memoizedValues(values, seenNodes, mapper);
    }

    // Perfect for fork/join-parallesism
    @Override
    protected <T> ValuationTree<T> memoizedMap(
      Function<? super Set<E>, ? extends Collection<? extends T>> mapper,
      Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls) {
      ValuationTree<T> mappedNode = memoizedCalls.get(this);

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
    protected Map<E, ValuationSet> memoizedInverse(ValuationSetFactory factory,
      Map<ValuationTree<E>, Map<E, ValuationSet>> memoizedCalls) {
      Map<E, ValuationSet> map = memoizedCalls.get(this);

      if (map != null) {
        return map;
      }

      var trueMask = factory.of(variable);
      var falseMask = trueMask.complement();
      var newMap = new HashMap<E, ValuationSet>();

      trueChild.memoizedInverse(factory, memoizedCalls).forEach(
        (key, set) -> newMap.merge(key, set.intersection(trueMask), ValuationSet::union));
      falseChild.memoizedInverse(factory, memoizedCalls).forEach(
        (key, set) -> newMap.merge(key, set.intersection(falseMask), ValuationSet::union));

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
      return String.format("(V: %d, tt: %s, ff: %s)", variable, falseChild, trueChild);
    }
  }
}
