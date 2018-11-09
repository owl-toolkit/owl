/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import owl.factories.ValuationSetFactory;

public abstract class ValuationTree<E> {
  private ValuationTree() {
  }

  public static <E> ValuationTree<E> of() {
    return (ValuationTree<E>) Leaf.EMPTY;
  }

  public static <E> ValuationTree<E> of(Collection<E> value) {
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

  public abstract Set<E> values();

  public Map<E, ValuationSet> inverse(ValuationSetFactory factory) {
    return memoizedInverse(factory, new HashMap<>());
  }

  public <T> ValuationTree<T> map(Function<? super Set<E>, ? extends Collection<T>> mapper) {
    return memoizedMap(mapper, new HashMap<>());
  }

  public static <L, R, E> ValuationTree<E> cartesianProduct(
    ValuationTree<L> leftTree,
    ValuationTree<R> rightTree,
    BiFunction<L, R, @Nullable E> merger) {
    return cartesianProduct(leftTree, rightTree, merger, new HashMap<>());
  }

  private static <L, R, E> ValuationTree<E> cartesianProduct(
    ValuationTree<L> leftTree,
    ValuationTree<R> rightTree,
    BiFunction<L, R, @Nullable E> merger,
    Map<List<?>, ValuationTree<E>> memoizedCalls) {
    var key = List.of(leftTree, rightTree);

    ValuationTree<E> productTree = memoizedCalls.get(key);

    if (productTree != null) {
      return productTree;
    }

    int leftVariable = leftTree instanceof Node
      ? ((Node<E>) leftTree).variable
      : Integer.MAX_VALUE;

    int rightVariable = rightTree instanceof Node
      ? ((Node<E>) rightTree).variable
      : Integer.MAX_VALUE;

    int variable = Math.min(leftVariable, rightVariable);

    if (variable == Integer.MAX_VALUE) {
      assert leftTree instanceof Leaf;
      assert rightTree instanceof Leaf;

      Set<E> elements = new HashSet<>();

      for (L leftValue : leftTree.values()) {
        for (R rightValue : rightTree.values()) {
          E element = merger.apply(leftValue, rightValue);

          if (element != null) {
            elements.add(element);
          }
        }
      }

      productTree = of(elements);
    } else {
      var falseCartesianProduct = cartesianProduct(
        descendFalseIf(leftTree, variable),
        descendFalseIf(rightTree, variable),
        merger, memoizedCalls);

      var trueCartesianProduct = cartesianProduct(
        descendTrueIf(leftTree, variable),
        descendTrueIf(rightTree, variable),
        merger, memoizedCalls);

      productTree = of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(key, productTree);
    return productTree;
  }

  private static <E> ValuationTree<E> descendFalseIf(ValuationTree<E> tree, int variable) {
    if (tree instanceof Node && ((Node<E>) tree).variable == variable) {
      return ((Node<E>) tree).falseChild;
    } else {
      return tree;
    }
  }

  private static <E> ValuationTree<E> descendTrueIf(ValuationTree<E> tree, int variable) {
    if (tree instanceof Node && ((Node<E>) tree).variable == variable) {
      return ((Node<E>) tree).trueChild;
    } else {
      return tree;
    }
  }

  protected abstract <T> ValuationTree<T> memoizedMap(
    Function<? super Set<E>, ? extends Collection<T>> mapper,
    Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls);

  protected abstract Map<E, ValuationSet> memoizedInverse(
    ValuationSetFactory factory,
    Map<ValuationTree<E>, Map<E, ValuationSet>> memoizedCalls);

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
    public Set<E> values() {
      return new HashSet<>(value);
    }

    @Override
    protected <T> ValuationTree<T> memoizedMap(Function<? super Set<E>,
      ? extends Collection<T>> mapper, Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls) {
      return memoizedCalls.computeIfAbsent(this, x -> of(mapper.apply(value)));
    }

    @Override
    protected Map<E, ValuationSet> memoizedInverse(ValuationSetFactory factory,
      Map<ValuationTree<E>, Map<E, ValuationSet>> memoizedCalls) {
      return memoizedCalls.computeIfAbsent(this, x -> Maps.asMap(value, y -> factory.universe()));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Leaf<?> leaf = (Leaf<?>) o;
      return value.equals(leaf.value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  public static final class Node<E> extends ValuationTree<E> {
    public final int variable;
    public final ValuationTree<E> trueChild;
    public final ValuationTree<E> falseChild;

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
    }

    @Override
    public Set<E> get(BitSet valuation) {
      return valuation.get(variable) ? trueChild.get(valuation) : falseChild.get(valuation);
    }

    @Override
    public Set<E> values() {
      Set<E> values = trueChild.values();
      values.addAll(falseChild.values());
      return values;
    }

    @Override
    protected <T> ValuationTree<T> memoizedMap(Function<? super Set<E>,
      ? extends Collection<T>> mapper, Map<ValuationTree<E>, ValuationTree<T>> memoizedCalls) {
      ValuationTree<T> mappedNode = memoizedCalls.get(this);

      if (mappedNode != null) {
        return mappedNode;
      }

      mappedNode = new Node<>(variable,
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

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      Node<?> node = (Node<?>) o;
      return variable == node.variable
        && trueChild.equals(node.trueChild)
        && falseChild.equals(node.falseChild);
    }

    @Override
    public int hashCode() {
      return Objects.hash(variable, trueChild, falseChild);
    }
  }
}
