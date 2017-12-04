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

import static owl.automaton.acceptance.BooleanExpressions.createDisjunction;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

/**
 * This class represents a Rabin acceptance. It consists of multiple {@link
 * RabinAcceptance.RabinPair}s, which in turn basically comprise a (potentially lazily allocated)
 * <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if it's <b>Inf</b> set is seen
 * infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding Rabin
 * acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public final class RabinAcceptance extends OmegaAcceptance {
  private static final int NOT_ALLOCATED = -1;
  private final BitSet allocatedIndices = new BitSet();
  private final List<RabinPair> pairs;

  public RabinAcceptance() {
    pairs = new ArrayList<>();
  }

  public RabinAcceptance(int pairNr) {
    pairs = new ArrayList<>();
    for (int i = 0; i < pairNr; i++) {
      createPair(2 * i, 2 * i + 1);
    }
  }

  public static RabinAcceptance of(BooleanExpression<AtomAcceptance> expression) {
    RabinAcceptance acceptance = new RabinAcceptance();

    if (expression.getType() == BooleanExpression.Type.EXP_FALSE) {
      // Empty rabin acceptance
      return acceptance;
    }

    for (BooleanExpression<AtomAcceptance> pair : BooleanExpressions.getDisjuncts(expression)) {
      int fin = NOT_ALLOCATED;
      int inf = NOT_ALLOCATED;

      for (BooleanExpression<AtomAcceptance> element : BooleanExpressions.getConjuncts(pair)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            fin = atom.getAcceptanceSet();
            break;
          case TEMPORAL_INF:
            inf = atom.getAcceptanceSet();
            break;
          default:
            assert false;
            break;
        }
      }

      acceptance.createPair(fin, inf);
    }

    return acceptance;
  }

  private RabinPair createPair(int fin, int inf) {
    RabinPair pair = new RabinPair(fin, inf);
    if (fin != NOT_ALLOCATED) {
      allocatedIndices.set(fin);
    }
    if (inf != NOT_ALLOCATED) {
      allocatedIndices.set(inf);
    }
    pairs.add(pair);
    return pair;
  }

  public RabinPair createPair() {
    int fin = allocatedIndices.nextClearBit(0);
    int inf = allocatedIndices.nextClearBit(fin + 1);
    return createPair(fin, inf);
  }

  @Override
  public int getAcceptanceSets() {
    return allocatedIndices.cardinality();
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    return createDisjunction(pairs.stream()
      .filter(x -> !x.isEmpty()).map(RabinPair::getBooleanExpression));
  }

  @Override
  public String getName() {
    return "Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    return List.of(pairs.size());
  }

  public List<RabinPair> getPairs() {
    return Collections.unmodifiableList(pairs);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() < 2 * pairs.size();
  }

  public static final class RabinPair {
    private final int finiteIndex;
    private final int infiniteIndex;

    RabinPair(int fin, int inf) {
      finiteIndex = fin;
      infiniteIndex = inf;
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
     * Checks whether the given edge is contained in the <b>Inf</b> set of this pair.
     *
     * @param edge
     *     The edge to be tested.
     *
     * @return If {@code edge} is contained in the <b>Inf</b> set.
     *
     * @see Edge#inSet(int)
     */
    public boolean containsInfinite(Edge<?> edge) {
      return hasInfinite() && edge.inSet(infiniteIndex);
    }

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      assert !isEmpty();
      if (finiteIndex == NOT_ALLOCATED) {
        return BooleanExpressions.mkInf(infiniteIndex);
      }
      if (infiniteIndex == NOT_ALLOCATED) {
        return BooleanExpressions.mkFin(finiteIndex);
      }
      return BooleanExpressions.mkFin(finiteIndex).and(BooleanExpressions.mkInf(infiniteIndex));
    }

    public int getFiniteIndex() {
      assert hasFinite();
      return finiteIndex;
    }

    public int getInfiniteIndex() {
      assert hasInfinite();
      return infiniteIndex;
    }

    /**
     * Checks if the <b>Fin</b> set of this pair is already used.
     */
    public boolean hasFinite() {
      return finiteIndex != NOT_ALLOCATED;
    }

    /**
     * Checks if the <b>Inf</b> set of this pair is already used.
     */
    public boolean hasInfinite() {
      return infiniteIndex != NOT_ALLOCATED;
    }

    /**
     * Checks if the <b>Fin</b> or <b>Inf</b> set of this pair are allocated.
     */
    public boolean isEmpty() {
      return finiteIndex == NOT_ALLOCATED && infiniteIndex == NOT_ALLOCATED;
    }
  }
}
