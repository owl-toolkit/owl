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
import static owl.automaton.acceptance.BooleanExpressions.createDisjunction;
import static owl.automaton.acceptance.BooleanExpressions.getConjuncts;
import static owl.automaton.acceptance.BooleanExpressions.getDisjuncts;

import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
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
import owl.automaton.MutableAutomaton;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
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

    for (BooleanExpression<AtomAcceptance> dis : getDisjuncts(expression)) {
      int fin = NOT_ALLOCATED;
      IntSortedSet inf = new IntAVLTreeSet();

      for (BooleanExpression<AtomAcceptance> element : getConjuncts(dis)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            checkArgument(fin == NOT_ALLOCATED);
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

      checkArgument(fin != NOT_ALLOCATED);
      checkArgument(inf.isEmpty() || inf.lastInt() - inf.firstInt() == inf.size() - 1);
      checkArgument(inf.isEmpty() || fin == inf.firstInt() - 1);
      acceptance.createPair(inf.size());
    }

    return acceptance;
  }

  public static void normalize(MutableAutomaton<?, GeneralizedRabinAcceptance> automaton) {
    GeneralizedRabinAcceptance acceptance = automaton.getAcceptance();

    acceptance.pairList.removeIf(GeneralizedRabinPair::isEmpty);
    Int2IntMap edgeRemapping = new Int2IntLinkedOpenHashMap();

    int currentIndex = 0;
    int currentShift = 0;
    for (int i = 0; i < acceptance.pairList.size(); i++) {
      GeneralizedRabinPair pair = acceptance.pairList.get(i);

      if (pair.hasFinite()) {
        int finalCurrentShift = currentShift;
        pair.forEachIndex(index -> edgeRemapping.put(index, index + finalCurrentShift));

        pair.shiftIndices(currentShift);
        currentIndex += 1 + pair.getInfiniteIndexCount();
      } else {
        currentShift += 1;
        int finalCurrentShift = currentShift;
        pair.forEachInfiniteIndex(index -> edgeRemapping.put(index, index + finalCurrentShift));

        pair.shiftIndices(currentShift);
        pair.setFiniteIndex(currentIndex);
        currentIndex += pair.getInfiniteIndexCount();
      }
    }
    if (!edgeRemapping.isEmpty()) {
      automaton.remapEdges((state, edge) -> Edges.remapAcceptance(edge, edgeRemapping));
    }
  }

  public GeneralizedRabinPair createPair(int infCount) {
    synchronized (mutex) {
      int finIndex = setCount;
      setCount += 1;

      GeneralizedRabinPair pair = new GeneralizedRabinPair(finIndex, setCount, setCount + infCount);
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
    return createDisjunction(pairList.stream().filter(x -> !x.isEmpty())
      .map(GeneralizedRabinPair::getBooleanExpression));
  }

  @Override
  public String getName() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    // <pair_count> <inf_pairs_of_1> <inf_pairs_of_2> <...>
    List<Object> extra = new ArrayList<>(pairList.size() + 1);
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

    private int finiteIndex;
    private int infiniteIndicesFrom;
    private int infiniteIndicesTo;

    GeneralizedRabinPair(int finiteIndex, int infiniteIndicesFrom, int infiniteIndicesTo) {
      assert finiteIndex >= 0;
      this.finiteIndex = finiteIndex;
      this.infiniteIndicesFrom = infiniteIndicesFrom;
      this.infiniteIndicesTo = infiniteIndicesTo;
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
      return finiteIndex;
    }

    public int getInfiniteIndex(int number) {
      assert infiniteIndicesFrom + number < infiniteIndicesTo;
      return infiniteIndicesFrom + number;
    }

    public int getInfiniteIndexCount() {
      return infiniteIndicesTo - infiniteIndicesFrom;
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

    public boolean isEmpty() {
      return !(hasFinite() || hasInfinite());
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

    void setFiniteIndex(int finiteIndex) {
      this.finiteIndex = finiteIndex;
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
      builder.append('(');
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
