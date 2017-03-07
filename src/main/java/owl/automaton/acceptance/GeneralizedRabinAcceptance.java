/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.automaton.acceptance;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaConsumerExtended;

public final class GeneralizedRabinAcceptance implements OmegaAcceptance {
  private final List<GeneralizedRabinPair> pairList;
  private int setCount = 0;

  public GeneralizedRabinAcceptance() {
    pairList = new LinkedList<>();
  }

  public GeneralizedRabinPair createPair() {
    GeneralizedRabinPair pair = new GeneralizedRabinPair(this, pairList.size());
    pairList.add(pair);
    return pair;
  }

  private int createSet() {
    int index = setCount;
    setCount += 1;
    return index;
  }

  @Override
  public int getAcceptanceSets() {
    return setCount;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    // GRA is EXISTS pair. (finitely often Fin set AND FORALL infSet. infinitely often inf set)
    if (pairList.isEmpty()) {
      // Empty EXISTS is false
      return new BooleanExpression<>(false);
    }

    BooleanExpression<AtomAcceptance> expression = null;

    for (GeneralizedRabinPair pair : pairList) {
      if (pair.isEmpty()) {
        continue;
      }
      BooleanExpression<AtomAcceptance> pairExpression = pair.getBooleanExpression();
      if (expression == null) {
        expression = pairExpression;
      } else {
        expression = expression.or(pairExpression);
      }
    }
    assert expression != null;
    return expression;
  }

  @Override
  public String getName() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    // <pair_count> <inf_pairs_of_1> <inf_pairs_of_2> <...>
    List<Object> extra = new ArrayList<>(getPairCount() + 1);
    extra.add(0); // Will be replaced by count of non-empty pairs.

    int nonEmptyPairs = 0;
    for (GeneralizedRabinPair pair : pairList) {
      if (pair.isEmpty()) {
        continue;
      }
      nonEmptyPairs += 1;
      extra.add(pair.getInfiniteSetCount());
    }
    extra.set(0, nonEmptyPairs);

    return extra;
  }

  public GeneralizedRabinAcceptance.GeneralizedRabinPair getPairByNumber(int pairNumber) {
    return pairList.get(pairNumber);
  }

  public int getPairCount() {
    return pairList.size();
  }

  /**
   * Returns an unmodifiable view of the pair collection.
   *
   * @return The rabin pairs of this acceptance condition
   */
  public Collection<GeneralizedRabinPair> getPairs() {
    return Collections.unmodifiableCollection(pairList);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.acceptanceSetStream().allMatch(index -> index < setCount);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(40);
    builder.append("GeneralisedRabinAcceptance: ");
    for (GeneralizedRabinPair pair : pairList) {
      builder.append(pair);
    }
    return builder.toString();
  }

  public static final class GeneralizedRabinPair {
    private final GeneralizedRabinAcceptance acceptance;
    private final IntList infiniteIndices;
    private final int pairNumber;
    private int finiteIndex;

    GeneralizedRabinPair(GeneralizedRabinAcceptance acceptance,
      int pairNumber) {
      this.acceptance = acceptance;
      this.pairNumber = pairNumber;
      this.infiniteIndices = new IntArrayList();
      finiteIndex = -1;
    }

    public boolean contains(int index) {
      return finiteIndex != -1 && finiteIndex == index //
        || infiniteIndices.contains(index);
    }

    /**
     * Checks whether the given edge is contained in the <b>Fin</b> set of this pair.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return If {@code edge} is contained in the <b>Fin</b> set.
     *
     * @see Edge#inSet(int)
     */
    public boolean containsFinite(Edge<?> edge) {
      return hasFinite() && edge.inSet(finiteIndex);
    }

    /**
     * Checks whether the given edge is contained in any <b>Inf</b> set of this pair.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return If {@code edge} is contained in any <b>Inf</b> set.
     *
     * @see Edge#inSet(int)
     */
    public boolean containsInfinite(Edge<?> edge) {
      // infiniteIndices.stream().anyMatch(edge::inSet), unrolled for performance
      for (int index : infiniteIndices) {
        if (edge.inSet(index)) {
          return true;
        }
      }
      return false;
    }

    public int createInfiniteSet() {
      int index = acceptance.createSet();
      infiniteIndices.add(index);
      return index;
    }

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      assert !isEmpty();

      BooleanExpression<AtomAcceptance> acceptance;
      if (finiteIndex == -1) {
        // Only inf sets
        acceptance = HoaConsumerExtended.mkInf(infiniteIndices.get(0));
      } else {
        acceptance = HoaConsumerExtended.mkFin(finiteIndex);
        if (!infiniteIndices.isEmpty()) {
          acceptance = acceptance.and(HoaConsumerExtended.mkInf(infiniteIndices.get(0)));
        }
      }
      for (int i = 1; i < infiniteIndices.size(); i++) {
        acceptance = acceptance.and(HoaConsumerExtended.mkInf(infiniteIndices.get(i)));
      }
      return acceptance;
    }

    public int getFiniteIndex() {
      assert hasFinite();
      return finiteIndex;
    }

    public IntList getInfiniteIndices() {
      // Not immutable-guarded for performance reasons, there is no ImmutableIntList
      return infiniteIndices;
    }

    public int getInfiniteSetCount() {
      return infiniteIndices.size();
    }

    public int getOrCreateFiniteIndex() {
      if (finiteIndex == -1) {
        // Not allocated yet
        this.finiteIndex = acceptance.createSet();
      }
      return finiteIndex;
    }

    public int getPairNumber() {
      return pairNumber;
    }

    public boolean hasFinite() {
      return finiteIndex != -1;
    }

    public boolean hasInfinite() {
      return !infiniteIndices.isEmpty();
    }

    /**
     * Returns the indices of all <b>Inf</b> contained in this edge.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return All indices of <b>Inf</b> sets contained in this edge.
     *
     * @see Edge#inSet(int)
     */
    public IntSet infiniteSetsOfEdge(Edge<?> edge) {
      // infiniteIndices.stream().filter(edge::inSet).collect(...), unrolled for performance
      IntSet set = new IntArraySet(infiniteIndices.size());
      for (int index : infiniteIndices) {
        if (edge.inSet(index)) {
          set.add(index);
        }
      }
      return set;
    }

    public boolean isEmpty() {
      return finiteIndex == -1 && infiniteIndices.isEmpty();
    }

    public boolean isFinite(int index) {
      return finiteIndex != -1 && finiteIndex == index;
    }

    public boolean isInfinite(int index) {
      return infiniteIndices.contains(index);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder((getInfiniteSetCount() + 1) * 3);
      builder.append('(').append(pairNumber).append(':');
      if (finiteIndex == -1) {
        builder.append('#');
      } else {
        builder.append(finiteIndex);
      }
      builder.append('|');
      if (infiniteIndices.isEmpty()) {
        builder.append('#');
      } else {
        builder.append(infiniteIndices.getInt(0));
        for (int i = 1; i < infiniteIndices.size(); i++) {
          builder.append(',').append(infiniteIndices.getInt(0));
        }
      }
      builder.append(')');
      return builder.toString();
    }
  }
}
