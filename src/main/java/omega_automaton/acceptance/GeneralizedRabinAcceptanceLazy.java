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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import omega_automaton.output.HOAConsumerExtended;

public final class GeneralizedRabinAcceptanceLazy implements OmegaAcceptance {
  private final List<GeneralizedRabinPair> pairList;
  private int setCount;

  public GeneralizedRabinAcceptanceLazy() {
    pairList = new LinkedList<>();
  }

  public GeneralizedRabinPair createPair() {
    final GeneralizedRabinPair pair = new GeneralizedRabinPair(this, pairList.size());
    pairList.add(pair);
    return pair;
  }

  public int getPairCount() {
    return pairList.size();
  }

  public GeneralizedRabinAcceptanceLazy.GeneralizedRabinPair getPairByNumber(int pairNumber) {
    return pairList.get(pairNumber);
  }

  @Override
  public String getName() {
    return "generalized-Rabin";
  }

  @Override
  public List<Object> getNameExtra() {
    // <pair_count> <inf_pairs_of_1> <inf_pairs_of_2> <...>
    final List<Object> extra = new ArrayList<>(getPairCount() + 1);
    extra.add(getPairCount());

    for (final GeneralizedRabinPair pair : pairList) {
      extra.add(pair.getInfiniteSetCount());
    }

    return extra;
  }

  @Override
  public int getAcceptanceSets() {
    return setCount;
  }

  @Override
  public BooleanExpression<AtomAcceptance> getBooleanExpression() {
    // GRA is EXISTS pair. (finitely often Fin set AND FORALL infSet. infinitely often inf set)
    BooleanExpression<AtomAcceptance> expression = null;

    for (final GeneralizedRabinPair pair : pairList) {
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

  /**
   * Returns an unmodifiable view of the pair collection
   *
   * @return The rabin pairs of this acceptance condition
   */
  public Collection<GeneralizedRabinPair> getPairs() {
    return Collections.unmodifiableCollection(pairList);
  }

  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("GeneralisedRabinAcceptance: ");
    for (final GeneralizedRabinPair pair : pairList) {
      builder.append(pair.toString());
    }
    return builder.toString();
  }

  private int createSet(final GeneralizedRabinPair pair) {
    final int index = setCount;
    setCount += 1;
    return index;
  }

  public static final class GeneralizedRabinPair {
    private final GeneralizedRabinAcceptanceLazy acceptance;
    private final int pairNumber;
    private final IntList infiniteIndices;
    private int finiteIndex;

    public GeneralizedRabinPair(final GeneralizedRabinAcceptanceLazy acceptance,
        final int pairNumber) {
      this.acceptance = acceptance;
      this.pairNumber = pairNumber;
      this.infiniteIndices = new IntArrayList();
      finiteIndex = -1;
    }

    public boolean hasFinite() {
      return finiteIndex != -1;
    }

    public boolean hasInfinite() {
      return !infiniteIndices.isEmpty();
    }

    public boolean isFinite(final int index) {
      return finiteIndex != -1 && finiteIndex == index;
    }

    public boolean isInfinite(final int index) {
      return infiniteIndices.contains(index);
    }

    public int getFiniteIndex() {
      assert hasFinite();
      return finiteIndex;
    }

    public int getOrCreateFiniteIndex() {
      if (finiteIndex == -1) {
        // Not allocated yet
        this.finiteIndex = acceptance.createSet(this);
      }
      return finiteIndex;
    }

    public int createInfiniteSet() {
      final int index = acceptance.createSet(this);
      infiniteIndices.add(index);
      return index;
    }

    public int getInfiniteSetCount() {
      return infiniteIndices.size();
    }

    public boolean isEmpty() {
      return finiteIndex == -1 && infiniteIndices.isEmpty();
    }

    public boolean contains(final int index) {
      return finiteIndex != -1 && finiteIndex == index //
          || infiniteIndices.contains(index);
    }

    public IntList getInfiniteIndices() {
      // Not immutable-guarded for performance reasons, there is no ImmutableIntList
      return infiniteIndices;
    }

    public int getPairNumber() {
      return pairNumber;
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder((getInfiniteSetCount() + 1) * 3);
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

    private BooleanExpression<AtomAcceptance> getBooleanExpression() {
      assert !isEmpty();

      BooleanExpression<AtomAcceptance> acceptance;
      if (finiteIndex == -1) {
        // Only inf sets
        acceptance = HOAConsumerExtended.mkInf(infiniteIndices.get(0));
      } else {
        acceptance = HOAConsumerExtended.mkFin(finiteIndex);
        if (!infiniteIndices.isEmpty()) {
          acceptance = acceptance.and(HOAConsumerExtended.mkInf(infiniteIndices.get(0)));
        }
      }
      for (int i = 1; i < infiniteIndices.size(); i++) {
        acceptance = acceptance.and(HOAConsumerExtended.mkInf(infiniteIndices.get(i)));
      }
      return acceptance;
    }
  }
}