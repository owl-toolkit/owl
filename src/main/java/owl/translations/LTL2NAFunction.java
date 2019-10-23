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
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.translations.canonical.NonDeterministicConstructionsPortfolio;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

public final class LTL2NAFunction implements Function<LabelledFormula, Automaton<?, ?>> {
  private static final Set<Class<? extends OmegaAcceptance>> SUPPORTED_ACCEPTANCE_CONDITIONS =
    Set.of(BuchiAcceptance.class, GeneralizedBuchiAcceptance.class, OmegaAcceptance.class);

  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;
  private final NonDeterministicConstructionsPortfolio<?> portfolio;

  public LTL2NAFunction(Environment environment) {
    this(OmegaAcceptance.class, environment);
  }

  public LTL2NAFunction(Class<? extends OmegaAcceptance> acceptance, Environment environment) {
    Preconditions.checkArgument(SUPPORTED_ACCEPTANCE_CONDITIONS.contains(acceptance),
      "%s is not in the set %s of supported acceptance conditions.",
      acceptance, SUPPORTED_ACCEPTANCE_CONDITIONS);

    Class<? extends GeneralizedBuchiAcceptance> castedAcceptance;

    if (OmegaAcceptance.class.equals(acceptance)
      || GeneralizedBuchiAcceptance.class.equals(acceptance)) {
      castedAcceptance = GeneralizedBuchiAcceptance.class;
    } else {
      castedAcceptance = BuchiAcceptance.class;
    }

    this.fallback = SymmetricNBAConstruction.of(environment, castedAcceptance);
    this.portfolio = new NonDeterministicConstructionsPortfolio<>(acceptance, environment);
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
