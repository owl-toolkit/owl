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
import java.util.function.Supplier;

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

public final class NBA2DPAFunction<S>
  implements Function<Automaton<S, GeneralizedBuchiAcceptance>, HoaPrintable> {

  private final EnumSet<Optimisation> optimisations;

  public NBA2DPAFunction() {
    this.optimisations = EnumSet.noneOf(Optimisation.class);
  }

  @Override
  public MutableAutomaton<RankingState<Set<S>, BreakpointState<S>>, ParityAcceptance>
  apply(Automaton<S, GeneralizedBuchiAcceptance> nba) {
    NBA2LDBAFunction<S> nba2ldba = new NBA2LDBAFunction<>(
      EnumSet.of(Optimisation.REMOVE_NON_ACCEPTING_COMPONENTS));
    
    LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Void> ldba =
      nba2ldba.apply(nba);
    
    LimitDeterministicAutomaton<Set<S>, BreakpointState<S>, BuchiAcceptance, Void>
    ldbaCutDet = ldba.asCutDeterministicAutomaton();
    
    Supplier<BreakpointState<S>> getSink = () -> BreakpointState.getSink();
    Supplier<BitSet> getEmptyBitSet = () -> new BitSet();
    AutomatonUtil.complete((MutableAutomaton<BreakpointState<S>, BuchiAcceptance>) ldbaCutDet
      .getAcceptingComponent(), getSink, getEmptyBitSet);
    
    LanguageLattice<Set<BreakpointState<S>>, BreakpointState<S>, Void> oracle =
      new SetLanguageLattice<>(ldbaCutDet);
    Predicate<Set<S>> clearRanking = s -> false;
    RankingAutomatonBuilder<Set<S>, BreakpointState<S>, Void, Set<BreakpointState<S>>> builder =
      new RankingAutomatonBuilder<>(ldbaCutDet, new AtomicInteger(), optimisations, oracle,
        clearRanking);
    builder.add(ldbaCutDet.getInitialComponent().getInitialState());
    return builder.build();
  }
}
