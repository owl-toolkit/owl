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

package owl.translations.ltl2ldba;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class LTL2LDBACliParser implements TransformerParser {
  public static final LTL2LDBACliParser INSTANCE = new LTL2LDBACliParser();

  private static final Option DEGENERALIZE = new Option("d", "degeneralize", false,
    "Construct a Büchi automaton instead of a generalised-Büchi automaton.");
  private static final Option EPSILON = new Option("e", "epsilon", false,
    "Do not remove generated epsilon-transitions. Note: The generated output is not valid HOA, "
      + "since the format does not support epsilon transitions.");
  private static final Option NON_DETERMINISTIC = new Option("n", "non-deterministic", false,
    "Construct a non-deterministic initial component instead of a deterministic.");

  private LTL2LDBACliParser() {}

  public static Option guessF() {
    return new Option("f", "guess-f", false,
      "Guess F-operators that are infinitely often true.");
  }

  public static Option simple() {
    return new Option("s", "simple", false,
      "Use a simpler state-space construction. This disables special optimisations and redundancy "
        + "removal.");
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2ldba")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .writer(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<Configuration> configuration = commandLine.hasOption(simple().getOpt())
      ? EnumSet.noneOf(Configuration.class)
      : EnumSet.of(Configuration.EAGER_UNFOLD, Configuration.FORCE_JUMPS,
        Configuration.OPTIMISED_STATE_STRUCTURE, Configuration.SUPPRESS_JUMPS);

    if (commandLine.hasOption(NON_DETERMINISTIC.getOpt())) {
      configuration.add(Configuration.NON_DETERMINISTIC_INITIAL_COMPONENT);
    }

    if (commandLine.hasOption(EPSILON.getOpt())) {
      configuration.add(Configuration.EPSILON_TRANSITIONS);
    }

    Function<Environment, Function<LabelledFormula, ? extends
      LimitDeterministicAutomaton<?, ?, ?, ?>>> translatorProvider;

    if (commandLine.hasOption(DEGENERALIZE.getOpt())) {
      if (commandLine.hasOption(guessF().getOpt())) {
        translatorProvider = environment ->
          LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translatorProvider = environment ->
          LTL2LDBAFunction.createDegeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    } else {
      if (commandLine.hasOption(guessF().getOpt())) {
        translatorProvider = environment ->
          LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(environment, configuration);
      } else {
        translatorProvider = environment ->
          LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(environment, configuration);
      }
    }

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      translatorProvider.apply(environment));
  }

  @Override
  public String getKey() {
    return "ltl2ldba";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to limit-deterministic Büchi automata";
  }

  @Override
  public Options getOptions() {
    Options options = new Options();
    List.of(DEGENERALIZE, EPSILON, NON_DETERMINISTIC, guessF(), simple())
      .forEach(options::addOption);
    return options;
  }
}
