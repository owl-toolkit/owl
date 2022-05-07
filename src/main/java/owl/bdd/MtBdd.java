/*
 * Copyright (C) 2018, 2022  (Salomon Sickert)
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

import static owl.logic.propositional.PropositionalFormula.falseConstant;
import static owl.logic.propositional.PropositionalFormula.trueConstant;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

/**
 * A multi-terminal binary decision diagram (MTBDD).
 *
 * <p>This class provides an implementation of ordered, but not necessarily reduced, MTBDDs.
 *
 * @param <E> the elements stored at the leaves of the MTBDD.
 */
public abstract sealed class MtBdd<E> {

  private MtBdd() {
  }

  @SuppressWarnings("unchecked")
  public static <E> MtBdd<E> of() {
    return (MtBdd<E>) Leaf.EMPTY;
  }

  public static <E> MtBdd<E> of(E value) {
    return new Leaf<>(Set.of(value));
  }

  public static <E> MtBdd<E> of(E value1, E value2) {
    return new Leaf<>(Set.of(value1, value2));
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  public static <E> MtBdd<E> of(E... values) {
    return new Leaf<>(values);
  }

  public static <E> MtBdd<E> copyOf(Collection<? extends E> value) {
    return value.isEmpty() ? of() : new Leaf<>(Set.copyOf(value));
  }

  public static <E> MtBdd<E> of(int variable, MtBdd<E> trueChild, MtBdd<E> falseChild) {
    if (trueChild.equals(falseChild)) {
      return trueChild;
    }

    return new Node<>(variable, trueChild, falseChild);
  }

  public static <E> MtBdd<E> of(Map<E, PropositionalFormula<Integer>> map) {
    ArrayList<E> keys = new ArrayList<>(map.size());
    ArrayList<PropositionalFormula<Integer>> values = new ArrayList<>(map.size());

    map.forEach((key, value) -> {
      keys.add(key);
      values.add(value);
    });

    return of(keys, values, new HashMap<>());
  }

  private static <E> MtBdd<E> of(
      ArrayList<E> keys,
      ArrayList<PropositionalFormula<Integer>> values,
      HashMap<ArrayList<PropositionalFormula<Integer>>, MtBdd<E>> cache) {

    var tree = cache.get(values);

    if (tree != null) {
      return tree;
    }

    int nextVariable = Integer.MAX_VALUE;

    for (PropositionalFormula<Integer> formula : values) {
      var variable = formula.minVariable(Integer::compareTo);
      if (variable != null) {
        nextVariable = Math.min(nextVariable, variable);
        Preconditions.checkArgument(0 <= nextVariable && nextVariable < Integer.MAX_VALUE);
      }
    }

    if (nextVariable == Integer.MAX_VALUE) {
      List<E> trueKeys = new ArrayList<>();

      for (int i = 0, s = keys.size(); i < s; i++) {
        var value = values.get(i);
        assert value.isTrue() || value.isFalse();
        if (value.isTrue()) {
          trueKeys.add(keys.get(i));
        }
      }

      // Cannot access E[]-constructor.
      @SuppressWarnings("unchecked")
      MtBdd<E> leaf = (MtBdd<E>) MtBdd.of(trueKeys.toArray());
      return leaf;
    }

    int smallestVariable = nextVariable;

    ArrayList<PropositionalFormula<Integer>> trueValues = new ArrayList<>(values.size());
    ArrayList<PropositionalFormula<Integer>> falseValues = new ArrayList<>(values.size());

    for (PropositionalFormula<Integer> value : values) {
      if (value.containsVariable(smallestVariable)) {
        trueValues.add(value.substitute(smallestVariable, trueConstant()));
        falseValues.add(value.substitute(smallestVariable, falseConstant()));
      } else {
        trueValues.add(value);
        falseValues.add(value);
      }
    }

    MtBdd<E> newTree = of(smallestVariable, of(keys, trueValues, cache), of(keys, falseValues, cache));
    cache.put(values, newTree);
    return newTree;
  }

  public abstract Set<E> get(BitSet valuation);

  public abstract Set<E> get(ImmutableBitSet valuation);

  /**
   * Collect all values in a single set. The return value of this method is not retained and
   * ownership is transferred to the callee.
   *
   * @return the set of all values.
   */
  public Set<E> flatValues() {
    Set<E> values = new HashSet<>();
    memoizedFlatValues(values, Collections.newSetFromMap(new IdentityHashMap<>()));
    return values;
  }

  // TODO: add allLeavesMatch().
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
      Function<? super Set<E>, ? extends Set<? extends T>> mapper) {
    return memoizedMap(mapper, new HashMap<>());
  }

  protected abstract <T> MtBdd<T> memoizedMap(
      Function<? super Set<E>, ? extends Set<? extends T>> mapper,
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

    private Leaf(E[] value) {
      this.value = Set.of(value);
    }

    private Leaf(Set<E> value) {
      this.value = Set.copyOf(value);
    }

    @Override
    public Set<E> get(BitSet valuation) {
      return value;
    }

    @Override
    public Set<E> get(ImmutableBitSet valuation) {
      return value;
    }

    @Override
    public Set<E> flatValues() {
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
        Function<? super Set<E>, ? extends Set<? extends T>> mapper,
        Map<MtBdd<E>, MtBdd<T>> memoizedCalls) {

      return memoizedCalls.computeIfAbsent(this, x -> copyOf(mapper.apply(value)));
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
      if (trueChild instanceof Node<E> trueNode) {
        Objects.checkIndex(variable, trueNode.variable);
      }

      if (falseChild instanceof Node<E> falseNode) {
        Objects.checkIndex(variable, falseNode.variable);
      }

      this.variable = variable;
      this.trueChild = trueChild;
      this.falseChild = falseChild;

      int result = (1000003) ^ variable;
      result = (1000003 * result) ^ trueChild.hashCode();
      result = (1000003 * result) ^ falseChild.hashCode();
      this.hashCode = result;
    }

    @Override
    public Set<E> get(BitSet valuation) {
      return valuation.get(variable) ? trueChild.get(valuation) : falseChild.get(valuation);
    }

    @Override
    public Set<E> get(ImmutableBitSet valuation) {
      return valuation.contains(variable) ? trueChild.get(valuation) : falseChild.get(valuation);
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
        Function<? super Set<E>, ? extends Set<? extends T>> mapper,
        Map<MtBdd<E>, MtBdd<T>> memoizedCalls) {

      MtBdd<T> mappedNode = memoizedCalls.get(this);

      if (mappedNode != null) {
        return mappedNode;
      }

      MtBdd<T> newTree = of(variable,
          trueChild.memoizedMap(mapper, memoizedCalls),
          falseChild.memoizedMap(mapper, memoizedCalls));
      memoizedCalls.put(this, newTree);
      return newTree;
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

      if (!(o instanceof Node<?> that)) {
        return false;
      }

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
