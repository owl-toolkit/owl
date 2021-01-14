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

import static java.util.stream.Collectors.toUnmodifiableSet;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ValuationTrees {
  private ValuationTrees() {
  }

  public static <E> ValuationTree<List<E>> cartesianProduct(List<ValuationTree<E>> trees) {
    switch (trees.size()) {
      case 0:
        return ValuationTree.of(Set.of(List.of()));

      case 1:
        return trees.get(0).map(
          x -> x.stream().map(List::of).collect(toUnmodifiableSet()));

      default:
        var iterator = trees.iterator();
        var productTree = cartesianProduct(iterator.next(), iterator.next(), List::of);

        while (iterator.hasNext()) {
          productTree = cartesianProduct(productTree, iterator.next(), Collections3::add);
        }

        return productTree;
    }
  }

  public static <K, V> ValuationTree<Map<K, V>> cartesianProduct(Map<K, ValuationTree<V>> trees) {
    switch (trees.size()) {
      case 0:
        return ValuationTree.of(Set.of(Map.of()));

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

  public static <E> ValuationTree<Set<E>> cartesianProduct(Set<ValuationTree<E>> trees) {
    switch (trees.size()) {
      case 0:
        return ValuationTree.of(Set.of(Set.of()));

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

  public static <L, R, E> ValuationTree<E> cartesianProduct(
    ValuationTree<L> factor1, ValuationTree<R> factor2, BiFunction<L, R, @Nullable E> combinator) {
    return cartesianProduct(factor1, factor2, combinator, new HashMap<>());
  }

  public static <T, R> ValuationTree<R> cartesianProduct(
    ValuationTree<T> factor1, ValuationTree<T> factor2, ValuationTree<T> factor3,
    TriFunction<T, T, T, @Nullable R> combinator) {

    return cartesianProduct(List.of(factor1, factor2, factor3)).map(x -> {
      if (x.isEmpty()) {
        return Set.of();
      }

      var y = x.iterator().next();
      return Collections3.ofNullable(combinator.apply(y.get(0), y.get(1), y.get(2)));
    });
  }

  public static <E> ValuationTree<E> union(Collection<ValuationTree<E>> trees) {
    switch (trees.size()) {
      case 0:
        return ValuationTree.of();

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

  public static <E> ValuationTree<E> union(ValuationTree<E> tree1, ValuationTree<E> tree2) {
    return union(tree1, tree2, new HashMap<>());
  }

  private static <L, R, E> ValuationTree<E> cartesianProduct(
    ValuationTree<L> leftTree, ValuationTree<R> rightTree, BiFunction<L, R, @Nullable E> merger,
    Map<List<ValuationTree<?>>, ValuationTree<E>> memoizedCalls) {
    var key = List.of(leftTree, rightTree);

    ValuationTree<E> cartesianProduct = memoizedCalls.get(key);

    if (cartesianProduct != null) {
      return cartesianProduct;
    }

    int variable = nextVariable(leftTree, rightTree);

    if (variable == Integer.MAX_VALUE) {
      Set<E> elements = new HashSet<>();

      for (L leftValue : ((ValuationTree.Leaf<L>) leftTree).value) {
        for (R rightValue : ((ValuationTree.Leaf<R>) rightTree).value) {
          E element = merger.apply(leftValue, rightValue);

          if (element != null) {
            elements.add(element);
          }
        }
      }

      cartesianProduct = ValuationTree.of(elements);
    } else {
      var falseCartesianProduct = cartesianProduct(
        descendFalseIf(leftTree, variable),
        descendFalseIf(rightTree, variable),
        merger, memoizedCalls);
      var trueCartesianProduct = cartesianProduct(
        descendTrueIf(leftTree, variable),
        descendTrueIf(rightTree, variable),
        merger, memoizedCalls);
      cartesianProduct = ValuationTree.of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(key, cartesianProduct);
    return cartesianProduct;
  }

  @SuppressWarnings("PMD")
  private static <T, R> ValuationTree<R> naryCartesianProduct(
    List<? extends ValuationTree<T>> trees,
    Function<? super List<T>, ? extends R> mapper,
    Map<? super List<ValuationTree<T>>, ValuationTree<R>> memoizedCalls) {

    ValuationTree<R> cartesianProduct = memoizedCalls.get(trees);

    if (cartesianProduct != null) {
      return cartesianProduct;
    }

    int variable = nextVariable(trees);

    if (variable == Integer.MAX_VALUE) {
      Set<R> elements = new HashSet<>();
      List<Set<T>> values = new ArrayList<>(trees.size());

      for (ValuationTree<T> x : trees) {
        assert x instanceof ValuationTree.Leaf;
        ValuationTree.Leaf<T> casted = (ValuationTree.Leaf<T>) x;
        values.add(casted.value);
      }

      for (List<T> values2 : Sets.cartesianProduct(values)) {
        R element = mapper.apply(values2);

        if (element != null) {
          elements.add(element);
        }
      }

      cartesianProduct = ValuationTree.of(elements);
    } else {
      var falseTrees = trees.stream()
        .map(x -> descendFalseIf(x, variable))
        .collect(Collectors.toUnmodifiableList());

      var trueTrees = trees.stream()
        .map(x -> descendTrueIf(x, variable))
        .collect(Collectors.toUnmodifiableList());

      var falseCartesianProduct = naryCartesianProduct(
        falseTrees,
        mapper, memoizedCalls);
      var trueCartesianProduct = naryCartesianProduct(
        trueTrees,
        mapper, memoizedCalls);
      cartesianProduct = ValuationTree.of(variable, trueCartesianProduct, falseCartesianProduct);
    }

    memoizedCalls.put(List.copyOf(trees), cartesianProduct);
    return cartesianProduct;
  }

  public static <K, T, R> ValuationTree<R> uncachedNaryCartesianProduct(
    Map<? extends K, ? extends ValuationTree<T>> trees,
    Function<Collection<Map.Entry<K, T>>, R> mapper) {

    List<K> lookup = new ArrayList<>(trees.size());
    List<ValuationTree<T>> treesNew = new ArrayList<>(trees.size());

    trees.forEach((k, t) -> {
      lookup.add(k);
      treesNew.add(t);
    });

    return naryCartesianProduct(treesNew, mapper, lookup);
  }

  private static <K, T, R> ValuationTree<R> naryCartesianProduct(
    List<ValuationTree<T>> trees,
    Function<Collection<Map.Entry<K, T>>, R> mapper,
    List<K> lookup) {

    int variable = nextVariable(trees);

    if (variable == Integer.MAX_VALUE) {
      Set<R> elements = new HashSet<>();
      List<List<Map.Entry<K, T>>> values = new ArrayList<>(trees.size());

      for (int i = 0, s = lookup.size(); i < s; i++) {
        var x = trees.get(i);
        var key = lookup.get(i);
        assert x instanceof ValuationTree.Leaf;
        ValuationTree.Leaf<T> casted = (ValuationTree.Leaf<T>) x;

        List<Map.Entry<K, T>> z = new ArrayList<>(casted.value.size());

        for (var y : casted.value) {
          z.add(Map.entry(key, y));
        }

        values.add(z);
      }

      for (Collection<Map.Entry<K, T>> values2 : Lists.cartesianProduct(values)) {
        R element = mapper.apply(values2);

        if (element != null) {
          elements.add(element);
        }
      }

      return ValuationTree.of(elements);
    }

    var falseTrees = new ArrayList<>(trees);
    falseTrees.replaceAll(x -> descendFalseIf(x, variable));
    var falseCartesianProduct = naryCartesianProduct(falseTrees, mapper, lookup);

    var trueTrees = new ArrayList<>(trees);
    trueTrees.replaceAll(x -> descendTrueIf(x, variable));
    var trueCartesianProduct = naryCartesianProduct(trueTrees, mapper, lookup);

    return ValuationTree.of(variable, trueCartesianProduct, falseCartesianProduct);
  }

  private static <E> ValuationTree<E> union(ValuationTree<E> tree1, ValuationTree<E> tree2,
    Map<Set<?>, ValuationTree<E>> memoizedCalls) {
    if (tree1.equals(tree2)) {
      return tree1;
    }

    var key = Set.of(tree1, tree2);

    ValuationTree<E> union = memoizedCalls.get(key);

    if (union != null) {
      return union;
    }

    int variable = nextVariable(tree1, tree2);

    if (variable == Integer.MAX_VALUE) {
      Set<E> value1 = ((ValuationTree.Leaf<E>) tree1).value;
      Set<E> value2 = ((ValuationTree.Leaf<E>) tree2).value;

      if (value1.isEmpty()) {
        union = tree2;
      } else if (value2.isEmpty()) {
        union = tree1;
      } else {
        union = ValuationTree.of(Set.copyOf(Sets.union(value1, value2)));
      }
    } else {
      var falseUnionProduct = union(
        descendFalseIf(tree1, variable),
        descendFalseIf(tree2, variable), memoizedCalls);
      var trueUnionProduct = union(
        descendTrueIf(tree1, variable),
        descendTrueIf(tree2, variable), memoizedCalls);
      union = ValuationTree.of(variable, trueUnionProduct, falseUnionProduct);
    }

    memoizedCalls.put(key, union);
    return union;
  }

  private static int nextVariable(ValuationTree<?> tree1, ValuationTree<?> tree2) {
    int variable1 = tree1 instanceof ValuationTree.Node
      ? ((ValuationTree.Node<?>) tree1).variable
      : Integer.MAX_VALUE;

    int variable2 = tree2 instanceof ValuationTree.Node
      ? ((ValuationTree.Node<?>) tree2).variable
      : Integer.MAX_VALUE;

    return Math.min(variable1, variable2);
  }

  private static int nextVariable(Collection<? extends ValuationTree<?>> trees) {
    int variable = Integer.MAX_VALUE;

    for (var tree : trees) {
      variable = Math.min(variable, tree instanceof ValuationTree.Node
        ? ((ValuationTree.Node<?>) tree).variable
        : Integer.MAX_VALUE);
    }

    return variable;
  }

  private static <E> ValuationTree<E> descendFalseIf(ValuationTree<E> tree, int variable) {
    if (tree instanceof ValuationTree.Node && ((ValuationTree.Node<E>) tree).variable == variable) {
      return ((ValuationTree.Node<E>) tree).falseChild;
    } else {
      return tree;
    }
  }

  private static <E> ValuationTree<E> descendTrueIf(ValuationTree<E> tree, int variable) {
    if (tree instanceof ValuationTree.Node && ((ValuationTree.Node<E>) tree).variable == variable) {
      return ((ValuationTree.Node<E>) tree).trueChild;
    } else {
      return tree;
    }
  }
}
