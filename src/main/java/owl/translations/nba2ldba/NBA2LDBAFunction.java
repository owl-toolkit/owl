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

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder.SetMultimapBuilder;
import java.util.EnumSet;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.LabelledEdge;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.automaton.output.HoaPrintable;
import owl.collections.ValuationSet;
import owl.translations.Optimisation;

public final class NBA2LDBAFunction<S>
  implements Function<Automaton<S, BuchiAcceptance>, HoaPrintable> {

  private final EnumSet<Optimisation> optimisations;

  public NBA2LDBAFunction() {
    this.optimisations = EnumSet.noneOf(Optimisation.class);
  }

  @Override
  public HoaPrintable apply(Automaton<S, BuchiAcceptance> nba) {
    if (nba.isDeterministic()) {
      return nba;
    }

    InitialComponentBuilder<S> initialComponentBuilder = InitialComponentBuilder.create(nba);
    AcceptingComponentBuilder<S> acceptingComponentBuilder = AcceptingComponentBuilder.create(nba);

    Function<S, Multimap<ValuationSet, S>> jump2 = (state) -> {
      Multimap<ValuationSet, S> jumps = SetMultimapBuilder.hashKeys().hashSetValues().build();

      for (LabelledEdge<S> labelledEdge : nba.getLabelledEdges(state)) {
        if (labelledEdge.edge.inSet(0)) {
          jumps.put(labelledEdge.valuations, labelledEdge.edge.getSuccessor());
        }
      }

      return jumps;
    };

    LimitDeterministicAutomatonBuilder<S, S, S, BreakpointState<S>, BuchiAcceptance, Void>
      builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder, (x) -> null, (x) -> null, optimisations, (x) -> true, jump2);

    return builder.build();
  }
}
