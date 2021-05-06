/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
 *
 * This file is part of Owl.
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

package owl.bdd;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Map;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

public interface BddSetFactory {

  BddSet of(boolean booleanConstant);

  BddSet of(int variable);

  default BddSet of(BitSet valuation, int upTo) {
    BitSet support = new BitSet();
    support.set(0, upTo);
    return of(valuation, support);
  }

  default BddSet of(BitSet valuation, BitSet support) {
    BddSet result = of(true);
    for (int i = support.nextSetBit(0); i != -1; i = support.nextSetBit(i + 1)) {
      result = result.intersection(valuation.get(i) ? of(i) : of(i).complement());
    }
    return result;
  }

  default BddSet union(BddSet... bddSets) {
    return Arrays.stream(bddSets).reduce(of(false), BddSet::union);
  }

  default BddSet intersection(BddSet... bddSets) {
    return Arrays.stream(bddSets).reduce(of(true), BddSet::intersection);
  }

  default BddSet of(BitSet valuation, ImmutableBitSet support) {
    return of(valuation, BitSet2.copyOf(support));
  }

  default BddSet of(ImmutableBitSet valuation, ImmutableBitSet support) {
    return of(BitSet2.copyOf(valuation), BitSet2.copyOf(support));
  }

  default BddSet of(PropositionalFormula<Integer> expression) {
    if (expression instanceof PropositionalFormula.Variable) {
      return of(((PropositionalFormula.Variable<Integer>) expression).variable);
    } else if (expression instanceof PropositionalFormula.Negation) {
      return of(((PropositionalFormula.Negation<Integer>) expression).operand).complement();
    } else if (expression instanceof PropositionalFormula.Conjunction) {
      return intersection(((PropositionalFormula.Conjunction<Integer>) expression).conjuncts.stream()
        .map(this::of)
        .toArray(BddSet[]::new));
    } else if (expression instanceof PropositionalFormula.Disjunction) {
      return union(((PropositionalFormula.Disjunction<Integer>) expression).disjuncts.stream()
        .map(this::of)
        .toArray(BddSet[]::new));
    } else {
      throw new AssertionError("Unreachable!");
    }
  }

  <S> MtBdd<S> toMtBdd(Map<? extends S, ? extends BddSet> sets);

}
