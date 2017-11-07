/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.nba2ldba;

import com.google.common.collect.Sets;
import java.util.EnumSet;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.ExploreBuilder;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.translations.Optimisation;

public final class NBA2LDBAFunction<S> implements Function<Automaton<S, ? extends OmegaAcceptance>,
  LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Safety>> {
  private final EnumSet<Optimisation> optimisations;

  public NBA2LDBAFunction(EnumSet<Optimisation> optimisations) {
    this.optimisations = optimisations;
  }

  @SuppressWarnings("unchecked")
  @Override
  public LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Safety> apply(
    Automaton<S, ? extends OmegaAcceptance> nba) {
    Automaton<S, GeneralizedBuchiAcceptance> nbaGBA;

    // TODO Module! Something like "transform-acc --to generalized-buchi"
    if (nba.getAcceptance() instanceof AllAcceptance) {
      nbaGBA = GeneralizedBuchiAcceptanceTransformer
        .create((Automaton<S, AllAcceptance>) nba).build();
    } else if (nba.getAcceptance() instanceof GeneralizedBuchiAcceptance) {
      nbaGBA = (Automaton<S, GeneralizedBuchiAcceptance>) nba;
    } else {
      throw new UnsupportedOperationException(nba.getAcceptance() + " is unsupported.");
    }

    InitialComponentBuilder<S> initialComponentBuilder = InitialComponentBuilder.create(nbaGBA);
    ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder
      = AcceptingComponentBuilder.createScc(nbaGBA);

    Function<BreakpointState<S>, Safety> stateSafety;

    if (optimisations.contains(Optimisation.COMPUTE_SAFETY_PROPERTY)) {
      nba.getStates().forEach(acceptingComponentBuilder::add);
      Automaton<BreakpointState<S>, BuchiAcceptance> acceptingComponent
        = acceptingComponentBuilder.build();

      stateSafety = state -> {
        if (SafetyUtil.isSafetyLanguage(state, acceptingComponent)) {
          return Safety.SAFETY;
        }
        if (SafetyUtil.isCosafetyLanguage(state, acceptingComponent)) {
          return Safety.CO_SAFETY;
        }
        return Safety.NEITHER;
      };
    } else {
      stateSafety = x -> null;
    }

    return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder, Sets::newHashSet, stateSafety, optimisations).build();
  }
}
