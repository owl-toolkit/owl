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

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaConsumerExtended;

/**
 * Generalized Rabin Acceptance - OR (Fin(i) and AND Inf(j)).
 *
 * <p>A generalized Rabin acceptance is formed by a disjunction of a conjunction between one Fin and
 * multiple Inf conditions.</p>
 *
 * <p>According to the HOA specifications, the indices are monotonically increasing and used for
 * exactly one Fin/Inf atom.</p>
 */
public final class GeneralizedRabinAcceptance implements OmegaAcceptance {
  private static final int NOT_ALLOCATED = -1;
  private final Object mutex = new Object();
  private final List<GeneralizedRabinPair> pairList;
  private int setCount = 0;

  public GeneralizedRabinAcceptance() {
    pairList = new LinkedList<>();
  }

  public static GeneralizedRabinAcceptance create(BooleanExpression<AtomAcceptance> expression) {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();

    for (BooleanExpression<AtomAcceptance> dis : BooleanExpressions.getDisjuncts(expression)) {
      int fin = NOT_ALLOCATED;
      IntSortedSet inf = new IntAVLTreeSet();

      for (BooleanExpression<AtomAcceptance> element : BooleanExpressions.getConjuncts(dis)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            fin = atom.getAcceptanceSet();
            break;
          case TEMPORAL_INF:
            inf.add(atom.getAcceptanceSet());
            break;
          default:
            assert false;
            break;
        }
      }

      checkArgument(inf.isEmpty() || inf.lastInt() - inf.firstInt() == inf.size() - 1);
      checkArgument(fin == NOT_ALLOCATED || inf.isEmpty() || fin == inf.firstInt() - 1);
      acceptance.createPair(fin != NOT_ALLOCATED, inf.size());
    }

    return acceptance;
  }

  public GeneralizedRabinPair createPair(boolean fin, int infCount) {
    synchronized (mutex) {
      int finIndex;
      if (fin) {
        finIndex = setCount;
        setCount += 1;
      } else {
        finIndex = NOT_ALLOCATED;
      }

      GeneralizedRabinPair pair =
        new GeneralizedRabinPair(pairList.size(), finIndex, setCount, setCount + infCount);
      setCount += infCount;
      pairList.add(pair);
      return pair;
    }
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
    if (expression == null) {
      return new BooleanExpression<>(false);
    }
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
      extra.add(pair.getInfiniteIndexCount());
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
    synchronized (mutex) {
      return edge.acceptanceSetStream().allMatch(index -> index < setCount);
    }
  }

  public void removeIndices(IntPredicate removalPredicate) {
    synchronized (mutex) {
      int removedIndices = 0;
      for (GeneralizedRabinPair pair : pairList) {
        int pairRemovedIndices = pair.removeIndices(removalPredicate);
        pair.shiftIndices(-removedIndices);
        removedIndices += pairRemovedIndices;
      }
      setCount -= removedIndices;
    }
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
    private final int pairNumber;
    private int finiteIndex;
    private int infiniteIndicesFrom;
    private int infiniteIndicesTo;

    GeneralizedRabinPair(int pairNumber, int finiteIndex, int infiniteIndicesFrom,
      int infiniteIndicesTo) {
      this.pairNumber = pairNumber;
      this.finiteIndex = finiteIndex;
      this.infiniteIndicesFrom = infiniteIndicesFrom;
      this.infiniteIndicesTo = infiniteIndicesTo;
    }

    public boolean contains(int index) {
      return isFinite(index) || isInfinite(index);
    }

    public boolean contains(Edge<?> edge) {
      return containsFinite(edge) || containsInfinite(edge);
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
      for (int index = infiniteIndicesFrom; index < infiniteIndicesTo; index++) {
        if (edge.inSet(index)) {
          return true;
        }
      }
      return false;
    }

    public void forEachIndex(IntConsumer action) {
      forFiniteIndex(action);
      forEachInfiniteIndex(action);
    }

    public void forEachInfiniteIndex(IntConsumer action) {
      for (int index = infiniteIndicesFrom; index < infiniteIndicesTo; index++) {
        action.accept(index);
      }
    }

    public void forFiniteIndex(IntConsumer action) {
      if (hasFinite()) {
        action.accept(finiteIndex);
      }
    }

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      assert !isEmpty();

      BooleanExpression<AtomAcceptance> acceptance;
      if (hasFinite()) {
        acceptance = HoaConsumerExtended.mkFin(finiteIndex);
        if (hasInfinite()) {
          acceptance = acceptance.and(HoaConsumerExtended.mkInf(infiniteIndicesFrom));
        }
      } else {
        acceptance = HoaConsumerExtended.mkInf(infiniteIndicesFrom);
      }
      for (int index = infiniteIndicesFrom + 1; index < infiniteIndicesTo; index++) {
        acceptance = acceptance.and(HoaConsumerExtended.mkInf(index));
      }

      return acceptance;
    }

    public int getFiniteIndex() {
      assert hasFinite();
      return finiteIndex;
    }

    public int getInfiniteIndex(int number) {
      assert infiniteIndicesFrom + number < infiniteIndicesTo;
      return infiniteIndicesFrom + number;
    }

    public int getInfiniteIndexCount() {
      return infiniteIndicesTo - infiniteIndicesFrom;
    }

    public int getPairNumber() {
      return pairNumber;
    }

    public boolean hasFinite() {
      return finiteIndex != NOT_ALLOCATED;
    }

    public boolean hasInfinite() {
      return infiniteIndicesFrom < infiniteIndicesTo;
    }

    public IntIterator infiniteIndexIterator() {
      return IntIterators.fromTo(infiniteIndicesFrom, infiniteIndicesTo);
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
      IntSet set = new IntArraySet(getInfiniteIndexCount());
      forEachInfiniteIndex(index -> {
        if (edge.inSet(index)) {
          set.add(index);
        }
      });
      return set;
    }

    public boolean isEmpty() {
      return !(hasFinite() || hasInfinite());
    }

    public boolean isFinite(int index) {
      return hasFinite() && finiteIndex == index;
    }

    public boolean isInfinite(int index) {
      return infiniteIndicesFrom <= index && index < infiniteIndicesTo;
    }

    int removeIndices(IntPredicate removal) {
      int removedIndices = 0;
      for (int index = infiniteIndicesFrom; index < infiniteIndicesTo; index++) {
        if (removal.test(index)) {
          removedIndices += 1;
        }
      }
      infiniteIndicesTo -= removedIndices;

      if (hasFinite() && removal.test(finiteIndex)) {
        finiteIndex = NOT_ALLOCATED;
        removedIndices += 1;
        infiniteIndicesFrom -= 1;
        infiniteIndicesTo -= 1;
      }
      return removedIndices;
    }

    void shiftIndices(int amount) {
      if (hasFinite()) {
        finiteIndex += amount;
        assert finiteIndex >= 0;
      }
      infiniteIndicesTo += amount;
      infiniteIndicesFrom += amount;
      assert infiniteIndicesFrom >= 0;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder((getInfiniteIndexCount() + 1) * 3);
      builder.append('(').append(pairNumber).append(':');
      if (hasFinite()) {
        builder.append(finiteIndex);
      } else {
        builder.append('#');
      }
      builder.append('|');
      if (hasInfinite()) {
        builder.append(infiniteIndicesFrom);
        for (int index = infiniteIndicesFrom + 1; index < infiniteIndicesTo; index++) {
          builder.append(',').append(index);
        }
      } else {
        builder.append('#');
      }
      builder.append(')');
      return builder.toString();
    }
  }

}
