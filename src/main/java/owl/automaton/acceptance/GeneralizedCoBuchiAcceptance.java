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

package owl.automaton.acceptance;

import static owl.logic.propositional.PropositionalFormula.Variable;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Negation;

public sealed class GeneralizedCoBuchiAcceptance extends EmersonLeiAcceptance
  permits CoBuchiAcceptance {

  GeneralizedCoBuchiAcceptance(int size) {
    super(size);
  }

  public static GeneralizedCoBuchiAcceptance of(int size) {
    return size == 1 ? CoBuchiAcceptance.INSTANCE : new GeneralizedCoBuchiAcceptance(size);
  }

  public static Optional<? extends GeneralizedCoBuchiAcceptance> ofPartial(
    PropositionalFormula<Integer> formula) {

    var usedSets = new BitSet();

    for (var disjunct : PropositionalFormula.disjuncts(formula)) {
      if (disjunct instanceof Negation<Integer> negation
        && negation.operand() instanceof Variable<Integer> variable) {
        usedSets.set(variable.variable());
      } else {
        return Optional.empty();
      }

    }

    if (usedSets.cardinality() == usedSets.length()) {
      return Optional.of(of(usedSets.length()));
    }

    return Optional.empty();
  }

  @Override
  protected final PropositionalFormula<Integer> lazyBooleanExpression() {
    return PropositionalFormula.Disjunction.of(IntStream.range(0, acceptanceSets())
      .mapToObj(x -> Negation.of(Variable.of(x)))
      .toList());
  }

  @Override
  public String name() {
    return "generalized-co-Buchi";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(acceptanceSets());
  }

  @Override
  public Optional<ImmutableBitSet> acceptingSet() {
    return acceptanceSets() == 0 ? Optional.empty() : Optional.of(ImmutableBitSet.of());
  }

  @Override
  public Optional<ImmutableBitSet> rejectingSet() {
    BitSet set = new BitSet();
    set.set(0, acceptanceSets());
    return Optional.of(ImmutableBitSet.copyOf(set));
  }
}
