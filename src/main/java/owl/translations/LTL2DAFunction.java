/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations;

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_HEURISTIC;
import static owl.translations.ltl2dpa.LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.run.Environment;
import owl.translations.canonical.BreakpointState;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.GenericConstructions;
import owl.translations.canonical.RoundRobinState;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;

public final class LTL2DAFunction implements Function<LabelledFormula, Automaton<?, ?>> {
  private final Environment environment;
  private final EnumSet<Constructions> allowedConstructions;
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;

  public LTL2DAFunction(Environment environment) {
    this(environment, false, EnumSet.allOf(Constructions.class));
  }

  public LTL2DAFunction(Environment environment, boolean onTheFly,
    EnumSet<Constructions> allowedConstructions) {
    this.allowedConstructions = EnumSet.copyOf(allowedConstructions);
    this.environment = environment;

    if (this.allowedConstructions.contains(Constructions.EMERSON_LEI)) {
      fallback = new DelagBuilder(environment);
    } else if (this.allowedConstructions.contains(Constructions.GENERALIZED_RABIN)) {
      fallback
        = SymmetricDRAConstruction.of(environment, RabinAcceptance.class, !onTheFly);
    } else if (this.allowedConstructions.contains(Constructions.RABIN)) {
      fallback
        = SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, !onTheFly);
    } else if (this.allowedConstructions.contains(Constructions.PARITY)) {
      var configuration = onTheFly
        ? EnumSet.of(COMPLEMENT_CONSTRUCTION_HEURISTIC)
        : EnumSet.copyOf(RECOMMENDED_ASYMMETRIC_CONFIG);

      fallback = new LTL2DPAFunction(environment, configuration);
    } else {
      fallback = x -> {
        throw new IllegalArgumentException("All allowed constructions exhausted.");
      };
    }
  }

  @Override
  public Automaton<?, ?> apply(LabelledFormula formula) {
    if (allowedConstructions.contains(Constructions.SAFETY)
      && SyntacticFragment.SAFETY.contains(formula)) {
      return safety(environment, formula);
    }

    if (allowedConstructions.contains(Constructions.CO_SAFETY)
      && SyntacticFragment.CO_SAFETY.contains(formula)) {
      return coSafety(environment, formula);
    }

    if (formula.formula() instanceof XOperator) {
      int xCount = 0;
      var unwrappedFormula = formula.formula();

      while (unwrappedFormula instanceof XOperator) {
        xCount++;
        unwrappedFormula = ((XOperator) unwrappedFormula).operand;
      }

      return GenericConstructions.delay(apply(formula.wrap(unwrappedFormula)), xCount);
    }

    if (allowedConstructions.contains(Constructions.BUCHI)
      || allowedConstructions.contains(Constructions.GENERALIZED_BUCHI)) {

      var formulas = formula.formula() instanceof Conjunction
        ? formula.formula().children()
        : Set.of(formula.formula());

      if (formulas.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
        return gfCoSafety(environment, formula,
          allowedConstructions.contains(Constructions.GENERALIZED_BUCHI));
      }

      if (SyntacticFragments.isGCoSafety(formula.formula())) {
        return gCoSafety(environment, formula);
      }
    }

    if (allowedConstructions.contains(Constructions.CO_BUCHI)) {
      var formulas = formula.formula() instanceof Disjunction
        ? formula.formula().children()
        : Set.of(formula.formula());

      if (formulas.size() > 1 && formulas.stream().allMatch(SyntacticFragments::isFgSafety)) {
        return fgSafetyInterleaved(environment, formula);
      }

      if (SyntacticFragments.isFgSafety(formula.formula())) {
        return fgSafety(environment, formula);
      }

      if (SyntacticFragments.isFSafety(formula.formula())) {
        return fSafety(environment, formula);
      }
    }

    return fallback.apply(formula);
  }

  public static Automaton<EquivalenceClass, AllAcceptance> safety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new DeterministicConstructions.Safety(factories, true, formula.formula());
  }

  public static Automaton<EquivalenceClass, BuchiAcceptance> coSafety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new DeterministicConstructions.CoSafety(factories, true, formula.formula());
  }

  public static Automaton<RoundRobinState<EquivalenceClass>, GeneralizedBuchiAcceptance>
  gfCoSafety(Environment environment, LabelledFormula formula, boolean generalized) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var formulas = formula.formula() instanceof Conjunction
      ? formula.formula().children()
      : Set.of(formula.formula());
    return new DeterministicConstructions.GfCoSafety(factories, true, formulas, generalized);
  }

  public static Automaton<EquivalenceClass, CoBuchiAcceptance> fgSafety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new DeterministicConstructions.FgSafety(factories, true, formula.formula());
  }

  public static Automaton<RoundRobinState<EquivalenceClass>, CoBuchiAcceptance> fgSafetyInterleaved(
    Environment environment, LabelledFormula formula) {
    var automaton = gfCoSafety(environment, formula.not(), false);
    var factory = automaton.onlyInitialState().state().factory();
    var complementAutomaton = Views.complement(automaton,
      RoundRobinState.of(0, factory.getFalse()));
    return AutomatonUtil.cast(complementAutomaton, CoBuchiAcceptance.class);
  }

  public static Automaton<BreakpointState<EquivalenceClass>, BuchiAcceptance> gCoSafety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new DeterministicConstructions.GCoSafety(factories, true, formula.formula());
  }

  public static Automaton<BreakpointState<EquivalenceClass>, CoBuchiAcceptance> fSafety(
    Environment environment, LabelledFormula formula) {
    var automaton = gCoSafety(environment, formula.not());
    var factory = automaton.onlyInitialState().current().factory();
    var complementAutomaton = Views.complement(automaton,
      BreakpointState.of(factory.getFalse(), factory.getFalse()));
    return AutomatonUtil.cast(complementAutomaton, CoBuchiAcceptance.class);
  }

  public enum Constructions {
    SAFETY, CO_SAFETY,
    BUCHI, GENERALIZED_BUCHI, CO_BUCHI,
    PARITY,
    RABIN, GENERALIZED_RABIN,
    EMERSON_LEI
  }
}
