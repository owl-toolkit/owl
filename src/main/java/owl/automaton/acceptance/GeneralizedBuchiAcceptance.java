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
import static owl.logic.propositional.PropositionalFormula.conjuncts;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;

public sealed class GeneralizedBuchiAcceptance extends EmersonLeiAcceptance
  permits AllAcceptance, BuchiAcceptance {

  GeneralizedBuchiAcceptance(int size) {
    super(size);
  }

  public static GeneralizedBuchiAcceptance of(int size) {
    return switch (size) {
      case 0 -> AllAcceptance.INSTANCE;
      case 1 -> BuchiAcceptance.INSTANCE;
      default -> new GeneralizedBuchiAcceptance(size);
    };
  }

  public static Optional<? extends GeneralizedBuchiAcceptance> ofPartial(
    PropositionalFormula<Integer> formula) {

    var usedSets = new BitSet();

    for (var conjunct : conjuncts(formula)) {
      if (conjunct instanceof Variable<Integer> variable) {
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
    return PropositionalFormula.Conjunction.of(IntStream.range(0, acceptanceSets())
      .mapToObj(Variable::of)
      .toList());
  }

  @Override
  public String name() {
    return "generalized-Buchi";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(acceptanceSets());
  }

  @Override
  public final Optional<ImmutableBitSet> acceptingSet() {
    BitSet set = new BitSet();
    set.set(0, acceptanceSets());
    return Optional.of(ImmutableBitSet.copyOf(set));
  }

  @Override
  public final Optional<ImmutableBitSet> rejectingSet() {
    return acceptanceSets() == 0 ? Optional.empty() : Optional.of(ImmutableBitSet.of());
  }
}
