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
    if (automaton.getAcceptance() instanceof AllAcceptance) {
      nba = new GeneralizedBuchiView<>((Automaton<S, AllAcceptance>) automaton).build();
    } else if (automaton.getAcceptance() instanceof GeneralizedBuchiAcceptance) {
      nba = (Automaton<S, GeneralizedBuchiAcceptance>) automaton;
    } else {
      throw new UnsupportedOperationException(automaton.getAcceptance() + " is unsupported.");
    }

    InitialComponentBuilder<S> initialComponentBuilder = InitialComponentBuilder.create(nba);
    MutableAutomatonBuilder<S, BreakpointState<S>, BuchiAcceptance> acceptingComponentBuilder
      = AcceptingComponentBuilder.createScc(nba);

    return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder, Sets::newHashSet,
      (Function<BreakpointState<S>, Void>) x -> null, configuration).build();
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2ldba")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }
}
