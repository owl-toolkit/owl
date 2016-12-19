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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.output.HOAConsumerExtended;

public class RabinAcceptanceLazy implements OmegaAcceptance {
  private final List<RabinPair> pairList;
  private int setCount;

  public RabinAcceptanceLazy() {
    pairList = new ArrayList<>();
  }

  public RabinPair createPair() {
    final RabinPair pair = new RabinPair(this, pairList.size());
    pairList.add(pair);
    return pair;
  }

  public int getPairCount() {
    return pairList.size();
  }

  /**
   * Returns an unmodifiable view of the pair collection
   *
   * @return The rabin pairs of this acceptance condition
   */
  public Collection<RabinPair> getPairs() {
    return Collections.unmodifiableCollection(pairList);
  }

  public RabinPair getPairByNumber(int pairNumber) {
    return pairList.get(pairNumber);
  }

  @Override
  public int getAcceptanceSets() {
    return setCount;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    // RA is EXISTS pair. (finitely often Fin set AND infinitely often Inf set)
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
    if (expression == null) {
      // Empty EXISTS is false
      return new BooleanExpression<>(false);
    }
    return expression;
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("RabinAcceptance: ");
    for (final RabinPair pair : pairList) {
      builder.append(pair.toString());
    }
    return builder.toString();
  }

  @Override
  public String getName() {
    return "Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    return Collections.singletonList(setCount);
  }

  private int createSet(final RabinPair pair) {
    final int index = setCount;
    setCount += 1;
    return index;
  }

  public static final class RabinPair {
    private final RabinAcceptanceLazy acceptance;
    private final int pairNumber;
    private int finiteIndex;
    private int infiniteIndex;

    public RabinPair(final RabinAcceptanceLazy acceptance, final int pairNumber) {
      this.acceptance = acceptance;
      this.pairNumber = pairNumber;
      finiteIndex = -1;
      infiniteIndex = -1;
    }

    public boolean hasFinite() {
      return finiteIndex != -1;
    }

    public boolean hasInfinite() {
      return infiniteIndex != -1;
    }

    public boolean isFinite(int index) {
      return finiteIndex != -1 && index == finiteIndex;
    }

    public boolean isInfinite(int index) {
      return infiniteIndex != -1 && index == infiniteIndex;
    }

    public int getOrCreateFiniteIndex() {
      if (finiteIndex == -1) {
        // Not allocated yet
        finiteIndex = acceptance.createSet(this);
      }
      return finiteIndex;
    }

    public int getOrCreateInfiniteIndex() {
      if (infiniteIndex == -1) {
        infiniteIndex = acceptance.createSet(this);
      }
      return infiniteIndex;
    }

    public boolean isEmpty() {
      return finiteIndex != -1 || infiniteIndex != -1;
    }

    public int getPairNumber() {
      return pairNumber;
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
  }
}
