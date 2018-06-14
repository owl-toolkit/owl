/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
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

import static owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration.REMOVE_EPSILON_TRANSITIONS;
import static owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder.Configuration;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class NBA2LDBA implements Function<Automaton<?, ?>,
  LimitDeterministicAutomaton<?, ?, BuchiAcceptance, Void>> {

  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("nba2ldba")
    .description("Converts a non-deterministic Büchi automaton into a limit-deterministic Büchi "
      + "automaton")
    .parser(settings -> {
      var ldbaConfiguration = EnumSet.of(
        REMOVE_EPSILON_TRANSITIONS,
        SUPPRESS_JUMPS_FOR_TRANSIENT_STATES);
      var function = new NBA2LDBA(false, ldbaConfiguration);
      return environment -> (input, context) ->
        function.apply(AutomatonUtil.cast(input, Object.class, OmegaAcceptance.class));
    }).build();

  private final boolean cutDeterministicAndComplete;
  private final EnumSet<Configuration> ldbaConfiguration;

  public NBA2LDBA(boolean cutDeterministicAndComplete, EnumSet<Configuration> ldbaConfiguration) {
    this.cutDeterministicAndComplete = cutDeterministicAndComplete;
    this.ldbaConfiguration = EnumSet.copyOf(ldbaConfiguration);
  }

  @Override
  public LimitDeterministicAutomaton<?, ?, BuchiAcceptance, Void> apply(
    Automaton<?, ?> automaton) {
    Automaton<Object, GeneralizedBuchiAcceptance> nba;

    if (automaton.acceptance() instanceof AllAcceptance) {
      var allAutomaton = Views.createPowerSetAutomaton(automaton, AllAcceptance.INSTANCE, true);
      var castedAutomaton = AutomatonUtil.cast(allAutomaton, Object.class, AllAcceptance.class);
      nba = Views.viewAs(castedAutomaton, GeneralizedBuchiAcceptance.class);
    } else if (automaton.acceptance() instanceof GeneralizedBuchiAcceptance) {
      nba = AutomatonUtil.cast(automaton, Object.class, GeneralizedBuchiAcceptance.class);
    } else {
      throw new UnsupportedOperationException(automaton.acceptance() + " is unsupported.");
    }

    if (cutDeterministicAndComplete) {
      var initialComponent = MutableAutomatonFactory.copy(
        Views.createPowerSetAutomaton(nba, NoneAcceptance.INSTANCE, false));
      var acceptingComponentBuilder = new AcceptingComponentBuilder<>(nba, true);
      return LimitDeterministicAutomatonBuilder.create(() -> initialComponent,
        acceptingComponentBuilder, x -> x, x -> (Void) null, ldbaConfiguration).build();
    } else {
      var ldbaOptional = AutomatonUtil.ldbaSplit(nba);

      if (ldbaOptional.isPresent()
        && ldbaOptional.get().acceptingComponent().acceptance() instanceof BuchiAcceptance) {
        return (LimitDeterministicAutomaton<?, ?, BuchiAcceptance, Void>)
          ((LimitDeterministicAutomaton) ldbaOptional.get());
      }

      var initialComponent = MutableAutomatonFactory.copy(Views.viewAsLts(nba));
      var acceptingComponentBuilder = new AcceptingComponentBuilder<>(nba, false);
      return LimitDeterministicAutomatonBuilder.create(() -> initialComponent,
        acceptingComponentBuilder, Set::of, x -> (Void) null, ldbaConfiguration).build();
    }
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("nba2ldba")
      .reader(InputReaders.HOA)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }
}
