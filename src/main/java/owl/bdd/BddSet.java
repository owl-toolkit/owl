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
import java.util.Iterator;
import java.util.Optional;
import java.util.function.IntUnaryOperator;
import owl.collections.ImmutableBitSet;
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

  default BddSet union(BddSet... bddSets) {
    BddSet result = this;
    for (BddSet bddSet : bddSets) {
      result = result.union(bddSet);
    }
    return result;
  }

  BddSet intersection(BddSet other);

  default BddSet intersection(BddSet... bddSets) {
    BddSet result = this;
    for (BddSet bddSet : bddSets) {
      result = result.intersection(bddSet);
    }
    return result;
  }

  <E> MtBdd<E> intersection(MtBdd<E> tree);

  default BddSet project(BitSet quantifiedAtomicPropositions) {
    return project(ImmutableBitSet.copyOf(quantifiedAtomicPropositions));
  }

  BddSet project(ImmutableBitSet quantifiedAtomicPropositions);

  BddSet relabel(IntUnaryOperator mapping);

  /*
   * Returns the support, i.e., the set of variables on which a decision is taken
   * for this BddSet.
   */
  BitSet support();

  PropositionalFormula<Integer> toExpression();

  /**
   * Returns a {@code Iterator<BitSet>}-view of this BddSet.
   *
   * <p>Since the number of variables is dynamic, an explicit support needs to be given. All
   * returned BitSets are a subset of this support.</p>
   *
   * @param support the upper-bound for all elements of the returned view.
   *
   * @return {@code Iterator<BitSet>}-view of this BddSet
   *
   * @throws IllegalArgumentException
   *     This method throws an exception if the {@param support}
   *     is smaller than the largest element of {@link BddSet#support()}.
   */
  Iterator<BitSet> iterator(int support);

  /**
   * Returns a {@code Iterator<BitSet>}-view of this BddSet.
   *
   * <p>Since the number of variables is dynamic, an explicit support needs to be given. All
   * returned BitSets are a subset of this support.</p>
   *
   * @param support the upper-bound for all elements of the returned view.
   *
   * @return {@code Iterator<BitSet>}-view of this BddSet
   *
   * @throws IllegalArgumentException
   *     This method throws an exception if the {@param support} does not contain all elements
   *     of {@link BddSet#support()}.
   */
  default Iterator<BitSet> iterator(BitSet support) {
    return iterator(ImmutableBitSet.copyOf(support));
  }

  /**
   * Returns a {@code Iterator<BitSet>}-view of this BddSet.
   *
   * <p>Since the number of variables is dynamic, an explicit support needs to be given. All
   * returned BitSets are a subset of this support.</p>
   *
   * @param support the upper-bound for all elements of the returned view.
   *
   * @return {@code Iterator<BitSet>}-view of this BddSet
   *
   * @throws IllegalArgumentException
   *     This method throws an exception if the {@param support} does not contain all elements
   *     of {@link BddSet#support()}.
   */
  Iterator<BitSet> iterator(ImmutableBitSet support);
}
