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

import static owl.automaton.acceptance.AcceptanceHelper.filterSuccessorFunction;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.algorithms.SccAnalyser;
import owl.automaton.AutomatonUtil;
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
  private final List<RabinPair> pairList;
  private int setCount = 0;

  public RabinAcceptance() {
    pairList = new ArrayList<>();
  }

  @Override
  public <S> boolean containsAcceptingRun(Set<S> scc,
    Function<S, Iterable<Edge<S>>> successorFunction) {
    assert AutomatonUtil.isScc(scc, successorFunction);

    IntSet noFinitePairs = new IntArraySet();
    List<RabinPair> finitePairs = new ArrayList<>();
    for (RabinPair rabinPair : pairList) {
      if (rabinPair.hasFinite()) {
        finitePairs.add(rabinPair);
      } else if (rabinPair.hasInfinite()) {
        noFinitePairs.add(rabinPair.getInfiniteIndex());
      }
    }

    if (!noFinitePairs.isEmpty()) {
      Function<S, Iterable<Edge<S>>> filteredSuccessorFunction =
        filterSuccessorFunction(successorFunction, scc);

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
        filterSuccessorFunction(successorFunction,
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

  /**
   * Creates a new Rabin pair.
   *
   * @return The created pair.
   */
  public RabinPair createPair() {
    RabinPair pair = new RabinPair(this, pairList.size());
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
    // RA is EXISTS pair. (finitely often Fin set AND infinitely often Inf set)
    if (pairList.isEmpty()) {
      // Empty EXISTS is false
      return new BooleanExpression<>(false);
    }
    BooleanExpression<AtomAcceptance> expression = null;

    for (RabinPair pair : pairList) {
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
    return "Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    // <number_of_sets>
    return ImmutableList.of(setCount);
  }

  /**
   * Returns the pair with the specified number, if present.
   *
   * @param pairNumber
   *     The number of the requested pair
   *
   * @return The corresponding pair.
   *
   * @throws java.util.NoSuchElementException
   *     If no pair with this particular number is present.
   * @see RabinPair#getPairNumber()
   */
  public RabinPair getPairByNumber(int pairNumber) {
    return pairList.get(pairNumber);
  }

  /**
   * Returns the amount of pairs this acceptance contains.
   *
   * @return The amount of Rabin pairs.
   */
  public int getPairCount() {
    return pairList.size();
  }

  /**
   * Returns an unmodifiable collection of all pairs in this acceptance.
   *
   * @return The rabin pairs of this acceptance condition.
   */
  public Set<RabinPair> getPairs() {
    return ImmutableSet.copyOf(pairList);
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.acceptanceSetStream().allMatch(index -> index < setCount);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(30);
    builder.append("RabinAcceptance: ");
    for (RabinPair pair : pairList) {
      builder.append(pair);
    }
    return builder.toString();
  }

  public static final class RabinPair {
    private final RabinAcceptance acceptance;
    private final int pairNumber;
    private int finiteIndex;
    private int infiniteIndex;

    RabinPair(RabinAcceptance acceptance, int pairNumber) {
      this.acceptance = acceptance;
      this.pairNumber = pairNumber;
      finiteIndex = -1;
      infiniteIndex = -1;
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
      if (finiteIndex == -1) {
        return HoaConsumerExtended.mkInf(infiniteIndex);
      }
      if (infiniteIndex == -1) {
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
     * Returns the index representing the <b>Fin</b> set of this pair, allocating it if required.
     */
    public int getOrCreateFiniteIndex() {
      if (finiteIndex == -1) {
        // Not allocated yet
        finiteIndex = acceptance.createSet();
      }
      return finiteIndex;
    }

    /**
     * Returns the index representing the <b>Inf</b> set of this pair, allocating it if required.
     */
    public int getOrCreateInfiniteIndex() {
      if (infiniteIndex == -1) {
        infiniteIndex = acceptance.createSet();
      }
      return infiniteIndex;
    }

    /**
     * Returns the number of this pair.
     */
    public int getPairNumber() {
      return pairNumber;
    }

    /**
     * Checks if the <b>Fin</b> set of this pair is already used.
     */
    public boolean hasFinite() {
      return finiteIndex != -1;
    }

    /**
     * Checks if the <b>Inf</b> set of this pair is already used.
     */
    public boolean hasInfinite() {
      return infiniteIndex != -1;
    }

    /**
     * Checks if the <b>Fin</b> or <b>Inf</b> set of this pair are allocated.
     */
    public boolean isEmpty() {
      return finiteIndex == -1 && infiniteIndex == -1;
    }

    /**
     * Returns if the specified {@code index} is the index representing this <b>Fin</b> set of this
     * pair.
     */
    public boolean isFinite(int index) {
      return finiteIndex != -1 && index == finiteIndex;
    }

    /**
     * Returns if the specified {@code index} is the index representing this <b>Inf</b> set of this
     * pair.
     */
    public boolean isInfinite(int index) {
      return infiniteIndex != -1 && index == infiniteIndex;
    }

    @Override
    public String toString() {
      if (isEmpty()) {
        return "";
      }

      StringBuilder builder = new StringBuilder(10);
      builder.append('(').append(pairNumber).append(':');
      if (finiteIndex == -1) {
        builder.append('#');
      } else {
        builder.append(finiteIndex);
      }
      builder.append('|');
      if (infiniteIndex == -1) {
        builder.append('#');
      } else {
        builder.append(infiniteIndex);
      }
      builder.append(')');
      return builder.toString();
    }
  }
}
