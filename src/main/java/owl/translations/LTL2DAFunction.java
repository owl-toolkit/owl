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

import static owl.translations.ltl2dpa.LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;

public final class LTL2DAFunction implements Function<LabelledFormula, Automaton<?, ?>> {
  private static final Set<Class<? extends OmegaAcceptance>> SUPPORTED_ACCEPTANCE_CONDITIONS =
    Set.of(EmersonLeiAcceptance.class, GeneralizedRabinAcceptance.class,
      RabinAcceptance.class, ParityAcceptance.class, OmegaAcceptance.class);

  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;
  private final DeterministicConstructionsPortfolio<?> portfolio;

  public LTL2DAFunction(Environment environment) {
    this(OmegaAcceptance.class, environment);
  }

  public LTL2DAFunction(Class<? extends OmegaAcceptance> acceptance, Environment environment) {
    Preconditions.checkArgument(SUPPORTED_ACCEPTANCE_CONDITIONS.contains(acceptance),
      "%s is not in the set %s of supported acceptance conditions.",
      acceptance, SUPPORTED_ACCEPTANCE_CONDITIONS);

    this.portfolio = new DeterministicConstructionsPortfolio<>(acceptance, environment);

    if (OmegaAcceptance.class.equals(acceptance) || EmersonLeiAcceptance.class.equals(acceptance)) {
      fallback = new DelagBuilder(environment);
    } else if (GeneralizedRabinAcceptance.class.equals(acceptance)) {
      fallback = SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true);
    } else if (RabinAcceptance.class.equals(acceptance)) {
      fallback = SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true);
    } else {
      assert ParityAcceptance.class.equals(acceptance);
      fallback = new LTL2DPAFunction(environment, EnumSet.copyOf(RECOMMENDED_ASYMMETRIC_CONFIG));
    }
  }

  @Override
  public Automaton<?, ?> apply(LabelledFormula formula) {
    var portfolioResult = portfolio.apply(formula);

    if (portfolioResult.isPresent()) {
      return portfolioResult.orElseThrow();
    }

    return fallback.apply(formula);
  }
}
