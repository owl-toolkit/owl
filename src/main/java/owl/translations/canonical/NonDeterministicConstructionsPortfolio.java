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

package owl.translations.canonical;

import java.util.Optional;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.bdd.FactorySupplier;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;

public final class NonDeterministicConstructionsPortfolio<A extends EmersonLeiAcceptance>
  extends AbstractPortfolio<A> {

  public NonDeterministicConstructionsPortfolio(Class<A> acceptance) {
    super(acceptance);
  }

  @Override
  public Optional<Automaton<?, ? extends A>> apply(LabelledFormula formula) {
    if (isAllowed(AllAcceptance.class)
      && SyntacticFragments.isSafety(formula.formula())) {
      return box(safety(formula));
    }

    if (isAllowed(BuchiAcceptance.class)
      && SyntacticFragments.isCoSafety(formula.formula())) {
      return box(coSafety(formula));
    }

    if (formula.formula() instanceof XOperator) {
      int xCount = 0;
      var unwrappedFormula = formula.formula();

      while (unwrappedFormula instanceof XOperator) {
        xCount++;
        unwrappedFormula = ((XOperator) unwrappedFormula).operand();
      }

      var xCountFinal = xCount;
      return apply(formula.wrap(unwrappedFormula))
        .map(x -> GenericConstructions.delay(x, xCountFinal));
    }

    var formulas = formula.formula() instanceof Conjunction
      ? formula.formula().operands
      : Set.of(formula.formula());

    if (isAllowed(GeneralizedBuchiAcceptance.class)
      && formulas.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
      return box(gfCoSafety(formula, true));
    }

    if (isAllowed(BuchiAcceptance.class)
      && formulas.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
      return box(gfCoSafety(formula, false));
    }

    if (isAllowed(BuchiAcceptance.class)
      && SyntacticFragments.isFgSafety(formula.formula())) {
      return box(fgSafety(formula));
    }

    return Optional.empty();
  }

  public static Automaton<Formula, BuchiAcceptance> coSafety(
    LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    return NonDeterministicConstructions.CoSafety.of(factories, formula.formula());
  }

  public static Automaton<Formula, AllAcceptance> safety(
    LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    return NonDeterministicConstructions.Safety.of(factories, formula.formula());
  }

  public static Automaton<RoundRobinState<Formula>, GeneralizedBuchiAcceptance> gfCoSafety(
    LabelledFormula formula, boolean generalized) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    var formulas = formula.formula() instanceof Conjunction
      ? Set.copyOf(formula.formula().operands)
      : Set.of(formula.formula());
    return NonDeterministicConstructions.GfCoSafety.of(factories, formulas, generalized);
  }

  public static Automaton<Formula, BuchiAcceptance> fgSafety(
    LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    return NonDeterministicConstructions.FgSafety.of(factories, formula.formula());
  }
}
