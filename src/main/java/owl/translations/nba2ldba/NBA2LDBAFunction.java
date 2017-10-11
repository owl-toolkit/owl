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

import java.util.BitSet;
import java.util.EnumSet;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
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

  @Override
  public LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Safety> apply(
    Automaton<S, ? extends OmegaAcceptance> nba) {
    Automaton<S, GeneralizedBuchiAcceptance> nbaGBA;

    if (nba.getAcceptance() instanceof AllAcceptance) {
      nbaGBA = GeneralizedBuchiAcceptanceTransformer
        .create((Automaton<S, AllAcceptance>) nba).build();
    } else if (nba.getAcceptance() instanceof GeneralizedBuchiAcceptance) {
      nbaGBA = (Automaton<S, GeneralizedBuchiAcceptance>) nba;
    } else {
      throw new UnsupportedOperationException(nba.getAcceptance() + " is unsupported.");
    }

    InitialComponentBuilder<S> initialComponentBuilder = InitialComponentBuilder.create(nbaGBA);
    LimitDeterministicAutomatonBuilder<S, S, S, BreakpointState<S>, BuchiAcceptance, Safety>
    builder;

    ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder
    = acceptingComponentBuilder = AcceptingComponentBuilder.createScc(nbaGBA);
    
    if (optimisations.contains(Optimisation.CALC_SAFETY)) {
      BiFunction<BreakpointState<S>, Automaton<BreakpointState<S>, BuchiAcceptance>, Safety>
      getSafeties = (state, automaton) -> {
        if (SafetyUtil.isSafetyLanguage(state, automaton)) {
          return Safety.SAFETY;
        }
        if (SafetyUtil.isCosafetyLanguage(state, automaton)) {
          return Safety.CO_SAFETY;
        }
        return Safety.NEITHER;
      };
      ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder2
      = acceptingComponentBuilder = AcceptingComponentBuilder.createScc(nbaGBA);
      for (S state : nba.getStates()) {
        BreakpointState<S> target = acceptingComponentBuilder2.add(state);
      }
      Automaton<BreakpointState<S>, BuchiAcceptance> acceptingComponent
      = acceptingComponentBuilder2.build();
      builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
          acceptingComponentBuilder, Sets::newHashSet, x -> getSafeties.apply(x,
              acceptingComponent), optimisations);
    } else {
      builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
          acceptingComponentBuilder, Sets::newHashSet, (x) -> null, optimisations);
    }
    
    return builder.build();
  }
}
