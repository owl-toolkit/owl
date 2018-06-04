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

import java.util.EnumSet;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ldba2dpa.FlatRankingAutomaton;
import owl.translations.nba2ldba.NBA2LDBA;

public final class NBA2DPA implements Function<Automaton<?, ?>, Automaton<?, ParityAcceptance>> {

  private final NBA2LDBA nba2ldba =
    new NBA2LDBA(true, EnumSet.of(Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES));

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("nba2dpa")
    .description("Converts a non-deterministic Büchi automaton into a deterministic parity "
      + "automaton")
    .parser(settings -> environment -> {
      NBA2DPA nba2dpa = new NBA2DPA();
      return (input, context) -> nba2dpa.apply(AutomatonUtil.cast(input));
    })
    .build();

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2dpa")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }

  @SuppressWarnings("unchecked")
  @Override
  public Automaton<?, ParityAcceptance> apply(Automaton<?, ?> automaton) {
    LimitDeterministicAutomaton<?, ?, BuchiAcceptance, Void> ldba = nba2ldba.apply(automaton);

    if (ldba.initialStates().isEmpty()) {
      return AutomatonFactory.singleton(automaton.factory(), new Object(),
        new ParityAcceptance(1, ParityAcceptance.Parity.MIN_ODD));
    }

    assert ldba.initialStates().size() == 1;
    SetLanguageLattice oracle = new SetLanguageLattice(ldba.acceptingComponent());
    return FlatRankingAutomaton.of(ldba, oracle, x -> false, false, true);
  }
}
