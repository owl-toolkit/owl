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
import java.util.Iterator;
import java.util.Map;
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

  BddSet restrict(BitSet restriction, BitSet support);

  BddSet relabel(IntUnaryOperator mapping);

  /*
   * Returns the support, i.e., the set of variables on which a decision is taken
   * for this BddSet.
   */
  BitSet support();

  PropositionalFormula<Integer> toExpression();

  default <S> MtBdd<S> toMtBdd(S terminal) {
    return factory().toMtBdd(Map.of(terminal, this));
  }

  default MtBdd<?> toMtBdd() {
    return toMtBdd(new Object());
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
   *     This method throws an exception if the {@param support}
   *     is smaller than the largest element of {@link BddSet#support()}.
   */
  default Iterator<BitSet> iterator(int support) {
    BitSet set = new BitSet();
    set.set(0, support);
    return iterator(set);
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
  default Iterator<BitSet> iterator(ImmutableBitSet support) {
    return new Iterator<>() {
      private BddSet current = BddSet.this;

      @Override
      public boolean hasNext() {
        return !current.isEmpty();
      }

      @Override
      public BitSet next() {
        BitSet next = current.element().orElseThrow();
        next.and(support.copyInto(new BitSet()));
        current = current.intersection(factory().of(next, support).complement());
        return next;
      }
    };
  }
}
