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

package owl.translations.nba2dpa;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.translations.Optimisation;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ldba2dpa.RankingAutomatonBuilder;
import owl.translations.ldba2dpa.RankingState;
import owl.translations.nba2ldba.BreakpointState;
import owl.translations.nba2ldba.NBA2LDBAFunction;
import owl.translations.nba2ldba.Safety;

public final class NBA2DPAFunction<S>
  implements Function<Automaton<S, GeneralizedBuchiAcceptance>, HoaPrintable> {

  private final EnumSet<Optimisation> optimisations;

  public NBA2DPAFunction() {
    this(EnumSet.noneOf(Optimisation.class));
  }

  public NBA2DPAFunction(EnumSet<Optimisation> optimisations) {
    this.optimisations = optimisations;
  }

  @Override
  public MutableAutomaton<RankingState<Set<S>, BreakpointState<S>>, ParityAcceptance>
  apply(Automaton<S, GeneralizedBuchiAcceptance> nba) {

    NBA2LDBAFunction<S> nba2ldba = new NBA2LDBAFunction<>(optimisations);

    LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Safety> ldba =
      nba2ldba.apply(nba);

    LimitDeterministicAutomaton<Set<S>, BreakpointState<S>, BuchiAcceptance, Safety>
    ldbaCutDet = ldba.asCutDeterministicAutomaton();

    AutomatonUtil.complete((MutableAutomaton<BreakpointState<S>, BuchiAcceptance>) ldbaCutDet
      .getAcceptingComponent(), BreakpointState::getSink, BitSet::new);

    LanguageLattice<Set<BreakpointState<S>>, BreakpointState<S>, Safety> oracle =
      new SetLanguageLattice<>(ldbaCutDet.getAcceptingComponent());
    Predicate<Set<S>> isAccepting = s -> false;
    RankingAutomatonBuilder<Set<S>, BreakpointState<S>, Safety, Set<BreakpointState<S>>> builder =
      new RankingAutomatonBuilder<>(ldbaCutDet, new AtomicInteger(), optimisations, oracle,
        isAccepting, false);
    builder.add(ldbaCutDet.getInitialComponent().getInitialState());

    MutableAutomaton<RankingState<Set<S>, BreakpointState<S>>, ParityAcceptance> dpa
    = builder.build();

    return dpa;
  }
}
