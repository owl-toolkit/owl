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

import static owl.translations.canonical.DeterministicConstructions.BreakpointStateAcceptingRoundRobin;
import static owl.translations.canonical.DeterministicConstructions.BreakpointStateRejectingRoundRobin;
import static owl.translations.canonical.DeterministicConstructions.CoSafety;
import static owl.translations.canonical.DeterministicConstructions.CoSafetySafetyRoundRobin;
import static owl.translations.canonical.DeterministicConstructions.GfCoSafety;
import static owl.translations.canonical.DeterministicConstructions.Safety;
import static owl.translations.canonical.DeterministicConstructions.SafetyCoSafetyRoundRobin;

import java.util.Optional;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.BooleanOperations;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.bdd.FactorySupplier;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;

public final class DeterministicConstructionsPortfolio<A extends EmersonLeiAcceptance>
  extends AbstractPortfolio<A> {

  public DeterministicConstructionsPortfolio(Class<A> acceptanceBound) {
    super(acceptanceBound);
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

    formulas = formula.formula() instanceof Disjunction
      ? formula.formula().operands
      : Set.of(formula.formula());

    if (isAllowed(GeneralizedCoBuchiAcceptance.class)
      && formulas.stream().allMatch(SyntacticFragments::isFgSafety)) {
      return box(fgSafety(formula, true));
    }

    if (isAllowed(CoBuchiAcceptance.class)
      && formulas.stream().allMatch(SyntacticFragments::isFgSafety)) {
      return box(fgSafety(formula, false));
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

    if (isAllowed(CoBuchiAcceptance.class)
      && formula.formula() instanceof Formula.TemporalOperator
      && SyntacticFragments.isCoSafetySafety(formula.formula())) {
      return box(coSafetySafety(formula));
    }

    if (isAllowed(BuchiAcceptance.class)
      && formula.formula() instanceof Formula.TemporalOperator
      && SyntacticFragments.isSafetyCoSafety(formula.formula())) {
      return box(safetyCoSafety(formula));
    }

    return Optional.empty();
  }

  public static Automaton<EquivalenceClass, AllAcceptance> safety(LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    return Safety.of(factories, formula.formula());
  }

  public static Automaton<EquivalenceClass, BuchiAcceptance> coSafety(LabelledFormula formula) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    return CoSafety.of(factories, formula.formula());
  }

  public static Automaton<RoundRobinState<EquivalenceClass>, GeneralizedBuchiAcceptance>
    gfCoSafety(LabelledFormula formula, boolean generalized) {
    var factories = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());
    var formulas = formula.formula() instanceof Conjunction
      ? Set.copyOf(formula.formula().operands)
      : Set.of(formula.formula());
    return GfCoSafety.of(factories, formulas, generalized);
  }

  public static Automaton<RoundRobinState<EquivalenceClass>, ? extends GeneralizedCoBuchiAcceptance>
    fgSafety(LabelledFormula formula, boolean generalized) {
    var automaton = gfCoSafety(formula.not(), generalized);
    return BooleanOperations
      .deterministicComplementOfCompleteAutomaton(automaton, GeneralizedCoBuchiAcceptance.class);
  }

  public static Automaton<BreakpointStateAcceptingRoundRobin, CoBuchiAcceptance>
    coSafetySafety(LabelledFormula formula) {
    return CoSafetySafetyRoundRobin.of(formula);
  }

  public static Automaton<BreakpointStateRejectingRoundRobin, BuchiAcceptance>
    safetyCoSafety(LabelledFormula formula) {
    return SafetyCoSafetyRoundRobin.of(formula);
  }
}
