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

package omega_automaton.acceptance;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.output.HOAConsumerExtended;
import owl.automaton.edge.Edge;

/**
 * This class represents a Rabin acceptance. It consists of multiple
 * {@link RabinAcceptance.RabinPair}s, which in turn basically comprise a (potentially lazily
 * allocated) <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if it's <b>Inf</b> set is
 * seen infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding
 * Rabin acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public class RabinAcceptance implements OmegaAcceptance {
  private final List<RabinPair> pairList;
  private int setCount;

  public RabinAcceptance() {
    pairList = new ArrayList<>();
  }

  /**
   * Creates a new Rabin pair.
   *
   * @return The created pair.
   */
  public RabinPair createPair() {
    final RabinPair pair = new RabinPair(this, pairList.size());
    pairList.add(pair);
    return pair;
  }

  private int createSet(final RabinPair pair) {
    final int index = setCount;
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

    for (final RabinPair pair : pairList) {
      if (pair.isEmpty()) {
        continue;
      }
      final BooleanExpression<AtomAcceptance> pairExpression = pair.getBooleanExpression();
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

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("RabinAcceptance: ");
    for (final RabinPair pair : pairList) {
      builder.append(pair.toString());
    }
    return builder.toString();
  }

  public static final class RabinPair {
    private final RabinAcceptance acceptance;
    private final int pairNumber;
    private int finiteIndex;
    private int infiniteIndex;

    private RabinPair(final RabinAcceptance acceptance, final int pairNumber) {
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
    public boolean containsFinite(final Edge<?> edge) {
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
    public boolean containsInfinite(final Edge<?> edge) {
      return hasInfinite() && edge.inSet(infiniteIndex);
    }

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      assert !isEmpty();
      if (finiteIndex == -1) {
        return HOAConsumerExtended.mkInf(infiniteIndex);
      }
      if (infiniteIndex == -1) {
        return HOAConsumerExtended.mkFin(finiteIndex);
      }
      return HOAConsumerExtended.mkFin(finiteIndex).and(HOAConsumerExtended.mkInf(infiniteIndex));
    }

    /**
     * Returns the index representing the <b>Fin</b> set of this pair, allocating it if required.
     */
    public int getOrCreateFiniteIndex() {
      if (finiteIndex == -1) {
        // Not allocated yet
        finiteIndex = acceptance.createSet(this);
      }
      return finiteIndex;
    }

    /**
     * Returns the index representing the <b>Inf</b> set of this pair, allocating it if required.
     */
    public int getOrCreateInfiniteIndex() {
      if (infiniteIndex == -1) {
        infiniteIndex = acceptance.createSet(this);
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

      final StringBuilder builder = new StringBuilder(10);
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
