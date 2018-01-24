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

import it.unimi.dsi.fastutil.ints.IntAVLTreeSet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntIterators;
import it.unimi.dsi.fastutil.ints.IntSortedSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;
import javax.annotation.Nonnegative;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

/**
 * Generalized Rabin Acceptance - OR (Fin(i) and AND Inf(j)).
 *
 * <p>A generalized Rabin acceptance is formed by a disjunction of a conjunction between one Fin and
 * multiple Inf conditions.</p>
 *
 * <p>According to the HOA specifications, the indices are monotonically increasing and used for
 * exactly one Fin/Inf atom.</p>
 */
public final class GeneralizedRabinAcceptance extends OmegaAcceptance {
  private final Object mutex = new Object();
  private final List<RabinPair> pairList;

  @Nonnegative
  private int setCount = 0;

  public GeneralizedRabinAcceptance() {
    pairList = new LinkedList<>();
  }

  private boolean assertConsistent() {
    int i = 0;

    for (RabinPair pair : pairList) {
      assert i == pair.finIndex;
      assert pair.finIndex <= pair.infIndex;
      i = pair.infIndex + 1;
    }

    assert i == setCount;
    return true;
  }

  public static GeneralizedRabinAcceptance of(BooleanExpression<AtomAcceptance> expression) {
    GeneralizedRabinAcceptance acceptance = new GeneralizedRabinAcceptance();

    for (BooleanExpression<AtomAcceptance> dis : getDisjuncts(expression)) {
      int fin = -1;
      IntSortedSet inf = new IntAVLTreeSet();

      for (BooleanExpression<AtomAcceptance> element : getConjuncts(dis)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            checkArgument(fin == -1);
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

      // TODO: This validation is not complete.
      checkArgument(fin != -1);
      checkArgument(fin == acceptance.setCount);
      checkArgument(inf.isEmpty() || inf.lastInt() - inf.firstInt() == inf.size() - 1);
      checkArgument(inf.isEmpty() || fin == inf.firstInt() - 1);
      acceptance.createPair(inf.size());
    }

    assert acceptance.assertConsistent();
    return acceptance;
  }

  public RabinPair createPair(@Nonnegative int infSets) {
    synchronized (mutex) {
      int finIndex = setCount;
      setCount = setCount + 1 + infSets;
      RabinPair pair = new RabinPair(finIndex, finIndex + infSets);
      pairList.add(pair);
      assert assertConsistent();
      return pair;
    }
  }

  @Override
  public int getAcceptanceSets() {
    return setCount;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    return createDisjunction(pairList.stream().map(RabinPair::getBooleanExpression));
  }

  @Override
  public String getName() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    // <pair_count> <inf_pairs_of_1> <inf_pairs_of_2> <...>
    List<Object> extra = new ArrayList<>(pairList.size() + 1);
    extra.add(pairList.size());

    for (RabinPair pair : pairList) {
      extra.add(pair.getInfiniteIndexCount());
    }

    return extra;
  }

  /**
   * Returns an unmodifiable view of the pair collection.
   *
   * @return The rabin pairs of this acceptance condition
   */
  public List<RabinPair> getPairs() {
    return Collections.unmodifiableList(pairList);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    synchronized (mutex) {
      return edge.largestAcceptanceSet() < setCount;
    }
  }

  public void removeIndices(IntPredicate removalPredicate) {
    synchronized (mutex) {
      int removedIndices = 0;
      Iterator<RabinPair> iterator = pairList.iterator();
      while (iterator.hasNext()) {
        RabinPair pair = iterator.next();

        if (removalPredicate.test(pair.finIndex)) {
          iterator.remove();
          removedIndices += pair.getInfiniteIndexCount() + 1;
        } else {
          int removedInfIndices = 0;

          for (int i = pair.finIndex + 1; i <= pair.infIndex; i++) {
            if (removalPredicate.test(i)) {
              removedInfIndices++;
            }
          }

          assert pair.finIndex >= removedIndices;
          assert pair.infIndex >= removedIndices + removedInfIndices;

          pair.finIndex = pair.finIndex - removedIndices;
          pair.infIndex = pair.infIndex - (removedIndices + removedInfIndices);
          removedIndices += removedInfIndices;
        }
      }

      setCount -= removedIndices;
      assert assertConsistent();
    }
  }

  public static final class RabinPair {

    @Nonnegative
    private int finIndex;

    @Nonnegative
    // All indices in the interval ]finIndex, infIndex] are considered inf.
    private int infIndex;

    RabinPair(@Nonnegative int finIndex, int infIndex) {
      assert finIndex >= 0;
      assert infIndex >= finIndex;
      this.finIndex = finIndex;
      this.infIndex = infIndex;
    }

    public boolean contains(Edge<?> edge) {
      return edge.inSet(finIndex) || containsInfinite(edge);
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
      for (int i = finIndex + 1; i <= infIndex; i++) {
        if (edge.inSet(i)) {
          return true;
        }
      }

      return false;
    }

    public void forEachIndex(IntConsumer action) {
      action.accept(finIndex);
      forEachInfiniteIndex(action);
    }

    public void forEachInfiniteIndex(IntConsumer action) {
      for (int i = finIndex + 1; i <= infIndex; i++) {
        action.accept(i);
      }
    }

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      BooleanExpression<AtomAcceptance> acceptance = BooleanExpressions.mkFin(finIndex);

      for (int index = finIndex + 1; index <= infIndex; index++) {
        acceptance = acceptance.and(BooleanExpressions.mkInf(index));
      }

      return acceptance;
    }

    @Nonnegative
    public int getFiniteIndex() {
      return finIndex;
    }

    @Nonnegative
    public int getInfiniteIndex(int number) {
      assert finIndex + number < infIndex;
      return finIndex + 1 + number;
    }

    @Nonnegative
    public int getInfiniteIndexCount() {
      return infIndex - finIndex;
    }

    public boolean hasInfinite() {
      return getInfiniteIndexCount() > 0;
    }

    public IntIterator infiniteIndexIterator() {
      return IntIterators.fromTo(finIndex + 1, infIndex + 1);
    }

    public boolean isInfinite(int i) {
      return finIndex < i && i <= infIndex;
    }
  }
}
