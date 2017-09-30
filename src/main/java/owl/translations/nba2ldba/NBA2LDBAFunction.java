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

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
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
  LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Void>> {

  private final EnumSet<Optimisation> optimisations;

  public NBA2LDBAFunction(EnumSet<Optimisation> optimisations) {
    this.optimisations = optimisations;
  }

  @Override
  public LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Void> apply(
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
    LimitDeterministicAutomatonBuilder<S, S, S, BreakpointState<S>, BuchiAcceptance, Void> builder;

    ExploreBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder;
    acceptingComponentBuilder = AcceptingComponentBuilder.createScc(nbaGBA);
    Function<S, Iterable<S>> epsilonJumpGenerator = (state) -> {
      Set<S> jumps = new HashSet<>();
      jumps.add(state);
      return jumps;
    };
    builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder, epsilonJumpGenerator, (x) -> null, optimisations);
    return builder.build();
  }
}
