/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import owl.logic.propositional.PropositionalFormula;

/**
 * Symbolic representation of a {@code Set<BitSet>}.
 *
 * <p> This interface does not extend {@code Set<BitSet>} in order to make it explicit whenever a
 * slow-path through the explicit representation is taken. </p>
 */
public interface BddSet {

  BddSetFactory factory();

  boolean isEmpty();

  boolean isUniverse();

  boolean contains(BitSet valuation);

  boolean containsAll(BddSet valuationSet);

  Optional<BitSet> element();

  BddSet complement();

  BddSet union(BddSet other);

  BddSet intersection(BddSet other);

  <E> MtBdd<E> intersection(MtBdd<E> tree);

  PropositionalFormula<Integer> toExpression();

  BddSet project(BitSet quantifiedAtomicPropositions);

  BddSet relabel(IntUnaryOperator mapping);

  /**
   * Returns an explicit Collection-compatible view of this BddSet. Note that iteration
   * and other operation on this set should not be used in performance sensitive-code.
   *
   * @return a set view.
   */
  Set<BitSet> toSet(int upTo);

  Set<BitSet> toSet(BitSet support);

  @Deprecated
  BddSet transferTo(BddSetFactory newFactory, IntUnaryOperator mapping);

  /*
   * Returns the support, i.e., the set of variables on which a decision is taken
   * for this BddSet.
   */
  BitSet support();
}
