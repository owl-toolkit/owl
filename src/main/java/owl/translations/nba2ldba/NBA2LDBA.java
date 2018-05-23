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
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.automaton.ldba.MutableAutomatonBuilder;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class NBA2LDBA<S> implements Function<Automaton<S, ?>,
  LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Void>> {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("nba2ldba")
    .description("Converts a non-deterministic Büchi automaton into a limit-deterministic Büchi "
      + "automaton")
    .parser(settings -> {
      NBA2LDBA<Object> function =
        new NBA2LDBA<>(EnumSet.of(Configuration.REMOVE_EPSILON_TRANSITIONS));
      return environment -> (input, context) ->
        function.apply(AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
    }).build();
  private final EnumSet<Configuration> configuration;

  public NBA2LDBA(EnumSet<Configuration> configuration) {
    this.configuration = configuration;
  }

  @SuppressWarnings("unchecked")
  @Override
  public LimitDeterministicAutomaton<S, BreakpointState<S>, BuchiAcceptance, Void> apply(
    Automaton<S, ?> automaton) {
    Automaton<S, GeneralizedBuchiAcceptance> nba;

    // TODO Module! Something like "transform-acc --to generalized-buchi"
    if (automaton.acceptance() instanceof AllAcceptance) {
      var buchi = BuchiView.build(AutomatonUtil.cast(automaton, AllAcceptance.class));
      nba = AutomatonUtil.cast(buchi, GeneralizedBuchiAcceptance.class);
    } else if (automaton.acceptance() instanceof GeneralizedBuchiAcceptance) {
      nba = AutomatonUtil.cast(automaton, GeneralizedBuchiAcceptance.class);
    } else {
      throw new UnsupportedOperationException(automaton.acceptance() + " is unsupported.");
    }

    InitialComponentBuilder<S> initialComponentBuilder = InitialComponentBuilder.create(nba);
    MutableAutomatonBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder
      = new AcceptingComponentBuilder<>(nba);

    return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder, Set::of, x -> (Void) null, configuration).build();
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2ldba")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }
}
