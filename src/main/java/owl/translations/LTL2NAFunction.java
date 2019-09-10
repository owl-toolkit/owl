/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

import com.google.common.base.Preconditions;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.ltl.Conjunction;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.run.Environment;
import owl.translations.canonical.GenericConstructions;
import owl.translations.canonical.NonDeterministicConstructions;
import owl.translations.canonical.RoundRobinState;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

public final class LTL2NAFunction implements Function<LabelledFormula, Automaton<?, ?>> {
  private static final Set<Class<? extends OmegaAcceptance>> SUPPORTED_ACCEPTANCE_CONDITIONS =
    Set.of(BuchiAcceptance.class, GeneralizedBuchiAcceptance.class);

  private final Class<? extends GeneralizedBuchiAcceptance> acceptance;
  private final Environment environment;
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;

  public LTL2NAFunction(Environment environment) {
    this(environment, GeneralizedBuchiAcceptance.class);
  }

  public LTL2NAFunction(Environment environment, Class<? extends OmegaAcceptance> acceptance) {
    Preconditions.checkArgument(SUPPORTED_ACCEPTANCE_CONDITIONS.contains(acceptance),
      "%s is not in the set %s of supported acceptance conditions.",
      acceptance, SUPPORTED_ACCEPTANCE_CONDITIONS);

    this.acceptance = BuchiAcceptance.class.equals(acceptance)
      ? BuchiAcceptance.class
      : GeneralizedBuchiAcceptance.class;
    this.environment = environment;
    this.fallback = SymmetricNBAConstruction.of(environment, this.acceptance);
  }

  @Override
  public Automaton<?, ?> apply(LabelledFormula formula) {
    if (SyntacticFragment.SAFETY.contains(formula)) {
      return safety(environment, formula);
    }

    if (SyntacticFragment.CO_SAFETY.contains(formula)) {
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

    var formulas = formula.formula() instanceof Conjunction
      ? formula.formula().children()
      : Set.of(formula.formula());

    if (formulas.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
      return gfCoSafety(environment, formula, acceptance.equals(GeneralizedBuchiAcceptance.class));
    }

    if (SyntacticFragments.isFgSafety(formula.formula())) {
      return fgSafety(environment, formula);
    }

    return fallback.apply(formula);
  }

  static Automaton<Formula, BuchiAcceptance> coSafety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new NonDeterministicConstructions.CoSafety(factories, formula.formula());
  }

  static Automaton<Formula, AllAcceptance> safety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new NonDeterministicConstructions.Safety(factories, formula.formula());
  }

  static Automaton<RoundRobinState<Formula>, GeneralizedBuchiAcceptance> gfCoSafety(
    Environment environment, LabelledFormula formula, boolean generalized) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var formulas = formula.formula() instanceof Conjunction
      ? formula.formula().children()
      : Set.of(formula.formula());
    return new NonDeterministicConstructions.GfCoSafety(factories, formulas, generalized);
  }

  static Automaton<Formula, BuchiAcceptance> fgSafety(
    Environment environment, LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    return new NonDeterministicConstructions.FgSafety(factories, formula.formula());
  }
}
