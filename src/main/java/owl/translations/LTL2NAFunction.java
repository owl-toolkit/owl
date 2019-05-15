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

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
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
  private final Environment environment;
  private final EnumSet<Constructions> allowedConstructions;
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;

  public LTL2NAFunction(Environment environment) {
    this(environment, EnumSet.allOf(Constructions.class));
  }

  public LTL2NAFunction(Environment environment, EnumSet<Constructions> allowedConstructions) {
    this.allowedConstructions = EnumSet.copyOf(allowedConstructions);
    this.environment = environment;

    if (this.allowedConstructions.contains(Constructions.GENERALIZED_BUCHI)) {
      fallback = SymmetricNBAConstruction
        .of(environment, GeneralizedBuchiAcceptance.class);
    } else if (this.allowedConstructions.contains(Constructions.BUCHI)) {
      fallback = SymmetricNBAConstruction
        .of(environment, BuchiAcceptance.class);
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

      if (SyntacticFragments.isFgSafety(formula.formula())) {
        return fgSafety(environment, formula);
      }
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

  public enum Constructions {
    SAFETY, CO_SAFETY, BUCHI, GENERALIZED_BUCHI
  }
}
