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

import java.util.BitSet;
import java.util.Map;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

public interface BddSetFactory {

  BddSet of(boolean booleanConstant);

  BddSet of(int variable);

  BddSet of(BitSet valuation, int upTo);

  BddSet of(BitSet valuation, BitSet support);

  BddSet union(BddSet... bddSets);

  BddSet intersection(BddSet... bddSets);

  default BddSet of(BitSet valuation, ImmutableBitSet support) {
    return of(valuation, BitSet2.copyOf(support));
  }

  default BddSet of(ImmutableBitSet valuation, ImmutableBitSet support) {
    return of(BitSet2.copyOf(valuation), BitSet2.copyOf(support));
  }

  BddSet of(PropositionalFormula<Integer> expression);

  <S> MtBdd<S> toMtBdd(Map<? extends S, ? extends BddSet> sets);

}
