/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.translations.modules;

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_EXACT;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2dpa.TypenessDPAConstruction;

public final class LTL2DPAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2dpa",
    "Translate LTL to deterministic parity automata. Either using an LDBA constructions "
      + "or a 'typeness' construction.",
    options(),
    (commandLine, environment) ->
      OwlModule.LabelledFormulaTransformer.of(translation(environment, commandLine))
  );

  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  private LTL2DPAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  private static Options options() {
    var ldbaOption = new Option(null, "ldba", false, "foo");
    ldbaOption.setOptionalArg(true);
    ldbaOption.setArgs(1);

    var iarOption = new Option(null, "iar", false, "bar");
    iarOption.setOptionalArg(true);
    iarOption.setArgs(1);

    var typeness = new Option(null, "typeness", false, "barz");
    typeness.setOptionalArg(true);
    typeness.setArgs(1);

    return new Options()
      .addOptionGroup(AbstractLTL2LDBAModule.getOptionGroup())
      .addOption(null, "disable-complement", false,
        "Disable the parallel computation of a DPA for the negation of the formula. If "
          + "the parallel computation is enabled, then two DPAs are computed and the "
          + "smaller (number of states) is returned. Only affects the LDBA-based constructions.")
      .addOption(AbstractLTL2PortfolioModule.disablePortfolio())
      .addOption(ldbaOption)
      .addOption(iarOption)
      .addOption(typeness);
  }

  public static Function<LabelledFormula, Automaton<?, ParityAcceptance>>
    translation(Environment environment, CommandLine commandLine) throws ParseException {


    boolean useComplement = !commandLine.hasOption("disable-complement");
    boolean usePortfolio = AbstractLTL2PortfolioModule.usePortfolio(commandLine);

    if (commandLine.hasOption("typeness")) {
      String argument = commandLine.getOptionValue("typeness");

      if (argument == null) {
        argument = "--symmetric";
      }

      var parser = new DefaultParser();
      var draCommandLine = parser.parse(
        AbstractLTL2DRAModule.options(), WHITESPACE_PATTERN.split(argument));
      var translation = LTL2DRAModule.translation(environment, draCommandLine);
      return typenessTranslation(translation);
    }

    if (commandLine.hasOption("iar")) {
      throw new UnsupportedOperationException("Not yet implemented");
    }

    String argument = null;

    if (commandLine.hasOption("ldba")) {
      argument = commandLine.getOptionValue("ldba");
    }

    if (argument == null) {
      argument = "--asymmetric";
    }

    var parser = new DefaultParser();
    var ldbaCommandLine = parser.parse(
      AbstractLTL2LDBAModule.options(), WHITESPACE_PATTERN.split(argument));

    boolean useSymmetric = ldbaCommandLine.hasOption(AbstractLTL2LDBAModule.symmetric().getOpt());
    return ldbaTranslation(environment, useSymmetric, useComplement, usePortfolio);
  }

  public static Function<LabelledFormula, Automaton<?, ParityAcceptance>> typenessTranslation(
    Function<LabelledFormula, Automaton<?, RabinAcceptance>> translation) {
    return new TypenessDPAConstruction(translation)::apply;
  }

  public static Function<LabelledFormula, Automaton<?, ParityAcceptance>> ldbaTranslation(
    Environment environment, boolean useSymmetric, boolean useComplement, boolean usePortfolio) {

    EnumSet<Configuration> configuration = EnumSet.of(OPTIMISE_INITIAL_STATE);

    if (useComplement) {
      configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
    }

    if (useSymmetric) {
      configuration.add(SYMMETRIC);
    }

    configuration.add(COMPRESS_COLOURS);

    var construction = new LTL2DPAFunction(environment, configuration);
    var portfolio = usePortfolio
      ? new DeterministicConstructionsPortfolio<>(ParityAcceptance.class, environment)
      : null;

    return labelledFormula -> {
      if (portfolio != null) {
        var automaton = portfolio.apply(labelledFormula);

        if (automaton.isPresent()) {
          return automaton.orElseThrow();
        }
      }

      return construction.apply(labelledFormula);
    };
  }
}
