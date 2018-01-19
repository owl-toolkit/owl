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
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.automaton.output.HoaPrintable;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.ldba2dpa.RankingAutomaton;
import owl.translations.nba2ldba.BreakpointState;
import owl.translations.nba2ldba.GeneralizedBuchiView;
import owl.translations.nba2ldba.NBA2LDBA;

public final class NBA2DPAFunction<S> implements Function<Automaton<S, ?>, HoaPrintable> {
  public static final TransformerParser CLI_PARSER = ImmutableTransformerParser.builder()
    .key("nba2dpa")
    .parser(settings -> environment -> {
      NBA2DPAFunction<Object> function = new NBA2DPAFunction<>();
      return (input, context) ->
        function.apply(AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
    })
    .build();


  public NBA2DPAFunction() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2dpa")
      .reader(InputReaders.HOA)
      .addTransformer(CLI_PARSER)
      .writer(OutputWriters.HOA)
      .build());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Automaton<?, ParityAcceptance> apply(Automaton<S, ?> nba) {
    Automaton<Object, GeneralizedBuchiAcceptance> nbaGBA;

    // TODO Module! Something like "transform-acc --to generalized-buchi"
    if (nba.getAcceptance() instanceof AllAcceptance) {
      nbaGBA = new GeneralizedBuchiView<>((Automaton<Object, AllAcceptance>) nba).build();
    } else if (nba.getAcceptance() instanceof GeneralizedBuchiAcceptance) {
      nbaGBA = (Automaton<Object, GeneralizedBuchiAcceptance>) nba;
    } else {
      throw new UnsupportedOperationException(nba.getAcceptance() + " is unsupported.");
    }

    nbaGBA = Views.complete(nbaGBA, new Object());

    NBA2LDBA<Object> nba2ldba = new NBA2LDBA<>(EnumSet.noneOf(Configuration.class));

    LimitDeterministicAutomaton<Set<Object>, BreakpointState<Object>, BuchiAcceptance, Void>
      ldbaCutDet = nba2ldba.apply(nbaGBA).asCutDeterministicAutomaton();
    AutomatonUtil.complete((MutableAutomaton<BreakpointState<Object>, BuchiAcceptance>) ldbaCutDet
      .getAcceptingComponent(), BreakpointState::getSink, BitSet::new);

    LanguageLattice<Set<BreakpointState<Object>>, BreakpointState<Object>, Void> oracle =
      new SetLanguageLattice<>(ldbaCutDet.getAcceptingComponent());

    return RankingAutomaton.of(ldbaCutDet, true, oracle, s -> false, false, true);
  }
}
