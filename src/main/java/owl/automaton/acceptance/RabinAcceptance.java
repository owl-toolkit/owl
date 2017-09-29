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

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomAcceptance.Type;
import jhoafparser.ast.BooleanExpression;
import owl.algorithms.SccAnalyser;
import owl.automaton.TransitionUtil;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaConsumerExtended;

/**
 * This class represents a Rabin acceptance. It consists of multiple
 * {@link RabinAcceptance.RabinPair}s, which in turn basically comprise a (potentially lazily
 * allocated) <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if it's <b>Inf</b> set is
 * seen infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding
 * Rabin acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public final class RabinAcceptance implements OmegaAcceptance {
  private static final int NOT_ALLOCATED = -1;
  private final BitSet allocatedIndices = new BitSet();
  private final List<RabinPair> pairs;

  public RabinAcceptance() {
    pairs = new ArrayList<>();
  }

  public static boolean checkWellformed(BooleanExpression<AtomAcceptance> acceptanceExpression) {
    if (acceptanceExpression.isAtom() || acceptanceExpression.isNOT()) {
      return false;
    }
    if (acceptanceExpression.isAND()) {
      if (acceptanceExpression.getLeft().isAtom() && acceptanceExpression.getRight().isAtom()) {
        return acceptanceExpression.getLeft().getAtom().getType() == Type.TEMPORAL_FIN
          && acceptanceExpression.getRight().getAtom().getType() == Type.TEMPORAL_INF;
      } else {
        return false;
      }
    }
    if (acceptanceExpression.isOR()) {
      return checkWellformed(acceptanceExpression.getLeft())
        && checkWellformed(acceptanceExpression.getRight());
    }
    return false;
  }

  public static RabinAcceptance create(BooleanExpression<AtomAcceptance> expression) {
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

  @Override
  public <S> boolean containsAcceptingRun(Set<S> scc,
    Function<S, Iterable<Edge<S>>> successorFunction) {
    IntSet noFinitePairs = new IntArraySet();
    List<RabinPair> finitePairs = new ArrayList<>();
    for (RabinPair rabinPair : pairs) {
      if (rabinPair.hasFinite()) {
        finitePairs.add(rabinPair);
      } else if (rabinPair.hasInfinite()) {
        noFinitePairs.add(rabinPair.getInfiniteIndex());
      }
    }

    if (!noFinitePairs.isEmpty()) {
      Function<S, Iterable<Edge<S>>> filteredSuccessorFunction =
        TransitionUtil.filterEdges(successorFunction, scc);

      // Check if there is any edge containing the infinite index of some pair with no finite index
      Predicate<Iterable<Edge<S>>> predicate = iter -> {
        for (Edge<S> edge : iter) {
          PrimitiveIterator.OfInt acceptanceIterator = edge.acceptanceSetIterator();
          while (acceptanceIterator.hasNext()) {
            if (noFinitePairs.contains(acceptanceIterator.nextInt())) {
              return true;
            }
          }
        }
        return false;
      };

      boolean anyNoFinitePairAccepts = scc.parallelStream()
        .map(filteredSuccessorFunction)
        .anyMatch(predicate);
      if (anyNoFinitePairAccepts) {
        return true;
      }
    }

    return !finitePairs.isEmpty() && finitePairs.parallelStream().anyMatch(finitePair -> {
      // Compute all SCCs after removing the finite edges of the current finite pair
      Function<S, Iterable<Edge<S>>> filteredSuccessorFunction =
        TransitionUtil.filterEdges(successorFunction,
          edge -> scc.contains(edge.getSuccessor()) && !finitePair.containsFinite(edge));

      return SccAnalyser.computeSccsWithEdges(scc, filteredSuccessorFunction)
        .stream().anyMatch(subScc -> {
          // Iterate over all edges inside the sub-SCC, check if there is any in the Inf set.
          for (S state : subScc) {
            for (Edge<S> edge : successorFunction.apply(state)) {
              if (!subScc.contains(edge.getSuccessor()) || finitePair.containsFinite(edge)) {
                // This edge does not qualify for an accepting cycle
                continue;
              }
              if (finitePair.containsInfinite(edge)) {
                // This edge yields an accepting cycle
                return true;
              }
            }
          }
          // No accepting edge was found in this sub-SCC
          return false;
        });
    });
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
    BooleanExpression<AtomAcceptance> expression = null;

    for (RabinPair pair : pairs) {
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
    return "Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    return ImmutableList.of(pairs.size());
  }

  public int getNumberOfPairs() {
    return pairs.size();
  }

  public RabinPair getPair(int index) {
    return pairs.get(index);
  }

  public List<RabinPair> getPairs() {
    return Collections.unmodifiableList(pairs);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.acceptanceSetStream().allMatch(index -> index < 2 * pairs.size());
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(30);
    builder.append("RabinAcceptance: ");
    for (RabinPair pair : pairs) {
      builder.append(pair);
    }
    return builder.toString();
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
        return HoaConsumerExtended.mkInf(infiniteIndex);
      }
      if (infiniteIndex == NOT_ALLOCATED) {
        return HoaConsumerExtended.mkFin(finiteIndex);
      }
      return HoaConsumerExtended.mkFin(finiteIndex).and(HoaConsumerExtended.mkInf(infiniteIndex));
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

    /**
     * Returns if the specified {@code index} is the index representing this <b>Fin</b> set of this
     * pair.
     */
    public boolean isFinite(int index) {
      return finiteIndex != NOT_ALLOCATED && index == finiteIndex;
    }

    /**
     * Returns if the specified {@code index} is the index representing this <b>Inf</b> set of this
     * pair.
     */
    public boolean isInfinite(int index) {
      return infiniteIndex != NOT_ALLOCATED && index == infiniteIndex;
    }

    @Override
    public String toString() {
      if (isEmpty()) {
        return "";
      }

      StringBuilder builder = new StringBuilder(10);
      builder.append('(');
      if (finiteIndex == NOT_ALLOCATED) {
        builder.append('#');
      } else {
        builder.append(finiteIndex);
      }
      builder.append('|');
      if (infiniteIndex == NOT_ALLOCATED) {
        builder.append('#');
      } else {
        builder.append(infiniteIndex);
      }
      builder.append(')');
      return builder.toString();
    }
  }
}
