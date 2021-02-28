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

package owl.automaton.acceptance;

import static owl.logic.propositional.PropositionalFormula.Disjunction;
import static owl.logic.propositional.PropositionalFormula.Variable;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.PropositionalFormula.Negation;

public class GeneralizedCoBuchiAcceptance extends EmersonLeiAcceptance {

  GeneralizedCoBuchiAcceptance(int size) {
    super(size, Disjunction.of(IntStream.range(0, size)
      .mapToObj(x -> Negation.of(Variable.of(x)))
      .collect(Collectors.toList())));
  }

  public static GeneralizedCoBuchiAcceptance of(int size) {
    return size == 1 ? CoBuchiAcceptance.INSTANCE : new GeneralizedCoBuchiAcceptance(size);
  }

  public static Optional<? extends GeneralizedCoBuchiAcceptance> ofPartial(
    PropositionalFormula<Integer> formula) {

    var usedSets = new BitSet();

    for (var disjunct : PropositionalFormula.disjuncts(formula)) {
      if (!(disjunct instanceof Negation
        && ((Negation<Integer>) disjunct).operand instanceof Variable)) {
        return Optional.empty();
      }

      usedSets.set(((Variable<Integer>) ((Negation<Integer>) disjunct).operand).variable);
    }

    if (usedSets.cardinality() == usedSets.length()) {
      return Optional.of(of(usedSets.length()));
    }

    return Optional.empty();
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
  public Optional<BitSet> acceptingSet() {
    return acceptanceSets() == 0 ? Optional.empty() : Optional.of(new BitSet(0));
  }

  @Override
  public Optional<BitSet> rejectingSet() {
    BitSet set = new BitSet();
    set.set(0, acceptanceSets());
    return Optional.of(set);
  }
}
