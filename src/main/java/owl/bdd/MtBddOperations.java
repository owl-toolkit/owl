/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;
import owl.collections.Collections3;
import owl.collections.Pair;

/**
 * This class provides operations for MTBDDs.
 */
public final class MtBddOperations {

  private MtBddOperations() {}

  public static <L, R, E> MtBdd<E> cartesianProduct(
    MtBdd<L> factor1, MtBdd<R> factor2, BiFunction<L, R, @Nullable E> combinator) {
    return cartesianProduct(factor1, factor2, combinator, new HashMap<>());
  }

  /**
   * This is a short-hack to fix issues that arise by empty sets in leafs. Only works for
   * leafs with at most one value.
   *
   * @param trees list of trees
   * @param <E> type
   * @return the lists might contain null.
   */
  public static <E> MtBdd<List<E>> cartesianProductWithNull(
    List<? extends MtBdd<E>> trees) {
    return naryCartesianProductWithNull(trees, new HashMap<>());
  }

  public static <E> MtBdd<List<E>> cartesianProduct(
    List<? extends MtBdd<E>> trees) {
    return naryCartesianProduct(trees, List::copyOf, new HashMap<>());
  }

  public static <K, V> MtBdd<Map<K, V>> cartesianProduct(
    Map<K, ? extends MtBdd<V>> trees) {

    switch (trees.size()) {
      case 0:
        return MtBdd.of(Set.of(Map.of()));

      case 1:
        var entry = trees.entrySet().iterator().next();
        return entry.getValue()
          .map(x -> x.stream().map(y -> Map.of(entry.getKey(), y)).collect(toUnmodifiableSet()));

      default:
        var iterator = trees.entrySet().iterator();
        var entry1 = iterator.next();
        var entry2 = iterator.next();
        var productTree = cartesianProduct(entry1.getValue(), entry2.getValue(),
          (value1, value2) -> Map.of(entry1.getKey(), value1, entry2.getKey(), value2));

        while (iterator.hasNext()) {
          var entryN = iterator.next();
          productTree = cartesianProduct(productTree, entryN.getValue(),
            (x, valueN) -> Map.copyOf(Collections3.add(x, entryN.getKey(), valueN)));
        }

        return productTree;
    }
  }

  public static <E> MtBdd<Set<E>> cartesianProduct(
    Set<? extends MtBdd<E>> trees) {

    switch (trees.size()) {
      case 0:
        return MtBdd.of(Set.of(Set.of()));

      case 1:
        return trees.iterator().next().map(
          x -> x.stream().map(Set::of).collect(toUnmodifiableSet()));

      default:
        var iterator = trees.iterator();
        var productTree = cartesianProduct(iterator.next(), iterator.next(),
          (x, y) -> x.equals(y) ? Set.of(x) : Set.of(x, y));

        while (iterator.hasNext()) {
          productTree = cartesianProduct(productTree, iterator.next(),
            (x, y) -> x.contains(y) ? x : Set.copyOf(Collections3.add(x, y)));
        }

        return productTree;
    }
  }

  public static <E> MtBdd<E> union(MtBdd<E> tree1, MtBdd<E> tree2) {
    return union(tree1, tree2, new HashMap<>());
  }

  public static <E> MtBdd<E> union(Collection<? extends MtBdd<E>> trees) {
    switch (trees.size()) {
      case 0:
        return MtBdd.of();

      case 1:
        return trees.iterator().next();

      default:
        var iterator = trees.iterator();
        var unionTree = union(iterator.next(), iterator.next());

        while (iterator.hasNext()) {
          unionTree = union(unionTree, iterator.next());
        }

        return unionTree;
    }
  }

  private static <L, R, E> MtBdd<E> cartesianProduct(
    MtBdd<L> leftTree, MtBdd<R> rightTree, BiFunction<L, R, @Nullable E> merger,
    Map<Pair<MtBdd<L>, MtBdd<R>>, MtBdd<E>> memoizedCalls) {
    var key = Pair.of(leftTree, rightTree);

    MtBdd<E> cartesianProduct = memoizedCalls.get(key);

    if (cartesianProduct != null) {
      return cartesianProduct;
    }

    int variable = nextVariable(leftTree, rightTree);

    if (variable == Integer.MAX_VALUE) {
      Set<E> elements = new HashSet<>();

      for (L leftValue : ((MtBdd.Leaf<L>) leftTree).value) {
        for (R rightValue : ((MtBdd.Leaf<R>) rightTree).value) {
          E element = merger.apply(leftValue, rightValue);

          if (element != null) {
            elements.add(element);
          }
        }
      }

      cartesianProduct = MtBdd.of(elements);
    } else {
      var falseCartesianProduct = cartesianProduct(
        descendFalseIf(leftTree, variable),
        descendFalseIf(rightTree, variable),
        merger, memoizedCalls);
      var trueCartesianProduct = cartesianProduct(
        descendTrueIf(leftTree, variable),
        descendTrueIf(rightTree, variable),
        merger, memoizedCalls);
      cartesianProduct = MtBdd.of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(key, cartesianProduct);
    return cartesianProduct;
  }

  private static <T> MtBdd<List<T>> naryCartesianProductWithNull(
    List<? extends MtBdd<T>> trees,
    Map<? super List<MtBdd<T>>, MtBdd<List<T>>> memoizedCalls) {

    var cartesianProduct = memoizedCalls.get(trees);

    if (cartesianProduct != null) {
      return cartesianProduct;
    }

    int variable = nextVariable(trees);

    if (variable == Integer.MAX_VALUE) {
      List<T> values = new ArrayList<>(trees.size());

      for (MtBdd<T> x : trees) {
        assert x instanceof MtBdd.Leaf;
        MtBdd.Leaf<T> casted = (MtBdd.Leaf<T>) x;
        Preconditions.checkArgument(casted.value.size() <= 1);
        values.add(Iterables.getOnlyElement(casted.value, null));
      }

      cartesianProduct = MtBdd.of(Set.of(Collections.unmodifiableList(values)));
    } else {
      var falseTrees = trees.stream()
        .map(x -> descendFalseIf(x, variable)).toList();

      var trueTrees = trees.stream()
        .map(x -> descendTrueIf(x, variable)).toList();

      var falseCartesianProduct = naryCartesianProductWithNull(
        falseTrees, memoizedCalls);
      var trueCartesianProduct = naryCartesianProductWithNull(
        trueTrees, memoizedCalls);
      cartesianProduct = MtBdd.of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(List.copyOf(trees), cartesianProduct);
    return cartesianProduct;
  }

  private static <T, R> MtBdd<R> naryCartesianProduct(
    List<? extends MtBdd<T>> trees,
    Function<? super List<T>, ? extends R> mapper,
    Map<? super List<MtBdd<T>>, MtBdd<R>> memoizedCalls) {

    MtBdd<R> cartesianProduct = memoizedCalls.get(trees);

    if (cartesianProduct != null) {
      return cartesianProduct;
    }

    int variable = nextVariable(trees);

    if (variable == Integer.MAX_VALUE) {
      Set<R> elements = new HashSet<>();
      List<Set<T>> values = new ArrayList<>(trees.size());

      for (MtBdd<T> x : trees) {
        assert x instanceof MtBdd.Leaf;
        MtBdd.Leaf<T> casted = (MtBdd.Leaf<T>) x;
        values.add(casted.value);
      }

      for (List<T> values2 : Sets.cartesianProduct(values)) {
        R element = mapper.apply(values2);

        if (element != null) {
          elements.add(element);
        }
      }

      cartesianProduct = MtBdd.of(elements);
    } else {
      var falseTrees = trees.stream()
        .map(x -> descendFalseIf(x, variable)).toList();

      var trueTrees = trees.stream()
        .map(x -> descendTrueIf(x, variable)).toList();

      var falseCartesianProduct = naryCartesianProduct(
        falseTrees,
        mapper, memoizedCalls);
      var trueCartesianProduct = naryCartesianProduct(
        trueTrees,
        mapper, memoizedCalls);
      cartesianProduct = MtBdd.of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(List.copyOf(trees), cartesianProduct);
    return cartesianProduct;
  }



  private static <E> MtBdd<E> union(MtBdd<E> tree1, MtBdd<E> tree2,
    Map<Set<?>, MtBdd<E>> memoizedCalls) {
    if (tree1.equals(tree2)) {
      return tree1;
    }

    var key = Set.of(tree1, tree2);

    MtBdd<E> union = memoizedCalls.get(key);

    if (union != null) {
      return union;
    }

    int variable = nextVariable(tree1, tree2);

    if (variable == Integer.MAX_VALUE) {
      Set<E> value1 = ((MtBdd.Leaf<E>) tree1).value;
      Set<E> value2 = ((MtBdd.Leaf<E>) tree2).value;

      if (value1.isEmpty()) {
        union = tree2;
      } else if (value2.isEmpty()) {
        union = tree1;
      } else {
        union = MtBdd.of(Set.copyOf(Sets.union(value1, value2)));
      }
    } else {
      var falseUnionProduct = union(
        descendFalseIf(tree1, variable),
        descendFalseIf(tree2, variable), memoizedCalls);
      var trueUnionProduct = union(
        descendTrueIf(tree1, variable),
        descendTrueIf(tree2, variable), memoizedCalls);
      union = MtBdd.of(variable, trueUnionProduct, falseUnionProduct);
    }

    memoizedCalls.put(key, union);
    return union;
  }

  private static int nextVariable(MtBdd<?> tree1, MtBdd<?> tree2) {
    int variable1 = tree1 instanceof MtBdd.Node
      ? ((MtBdd.Node<?>) tree1).variable
      : Integer.MAX_VALUE;

    int variable2 = tree2 instanceof MtBdd.Node
      ? ((MtBdd.Node<?>) tree2).variable
      : Integer.MAX_VALUE;

    return Math.min(variable1, variable2);
  }

  private static int nextVariable(Collection<? extends MtBdd<?>> trees) {
    int variable = Integer.MAX_VALUE;

    for (var tree : trees) {
      variable = Math.min(variable, tree instanceof MtBdd.Node
        ? ((MtBdd.Node<?>) tree).variable
        : Integer.MAX_VALUE);
    }

    return variable;
  }

  private static <E> MtBdd<E> descendFalseIf(MtBdd<E> tree, int variable) {
    if (tree instanceof MtBdd.Node && ((MtBdd.Node<E>) tree).variable == variable) {
      return ((MtBdd.Node<E>) tree).falseChild;
    } else {
      return tree;
    }
  }

  private static <E> MtBdd<E> descendTrueIf(MtBdd<E> tree, int variable) {
    if (tree instanceof MtBdd.Node && ((MtBdd.Node<E>) tree).variable == variable) {
      return ((MtBdd.Node<E>) tree).trueChild;
    } else {
      return tree;
    }
  }
}
