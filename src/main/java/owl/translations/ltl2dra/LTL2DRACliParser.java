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

package owl.translations.ltl2dra;

import static owl.run.modules.OwlModuleParser.TransformerParser;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.EXISTS_SAFETY_CORE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISED_STATE_STRUCTURE;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dra.LTL2DRAFunction.Configuration;
import owl.translations.ltl2ldba.LTL2LDBACliParser;

// Kept separate from LTL2DRAFunction so that it can be used by JNI without loading the run package
public final class LTL2DRACliParser implements TransformerParser {
  public static final LTL2DRACliParser INSTANCE = new LTL2DRACliParser();
  private static final Option DEGENERALIZE = new Option("d", "degeneralize", false,
    "Construct a Rabin automaton instead of a generalised-Rabin automaton (smaller than "
      + "general-purpose degeneralization).");

  private LTL2DRACliParser() {
  }

  @Override
  public String getKey() {
    return "ltl2dra";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to deterministic (generalized) Rabin automata, using an LDBA "
      + "construction";
  }

  @Override
  public Options getOptions() {
    return new Options().addOption(LTL2LDBACliParser.simple()).addOption(DEGENERALIZE);
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<LTL2DRAFunction.Configuration> configuration;

    if (commandLine.hasOption(LTL2LDBACliParser.simple().getOpt())) {
      configuration = EnumSet.noneOf(LTL2DRAFunction.Configuration.class);
    } else {
      configuration = EnumSet.of(EXISTS_SAFETY_CORE, OPTIMISED_STATE_STRUCTURE,
        OPTIMISE_INITIAL_STATE);
    }

    if (commandLine.hasOption(DEGENERALIZE.getOpt())) {
      configuration.add(Configuration.DEGENERALIZE);
    }

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      new LTL2DRAFunction(environment, configuration));
  }


  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2dra")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
