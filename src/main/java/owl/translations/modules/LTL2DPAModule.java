/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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
import org.apache.commons.cli.Options;
import owl.automaton.acceptance.optimizations.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;

public final class LTL2DPAModule {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2dpa",
    "Translate LTL to deterministic parity automata, using LDBA constructions.",
    options(),
    (commandLine, environment) -> {
      EnumSet<Configuration> configuration = EnumSet.of(OPTIMISE_INITIAL_STATE);

      if (commandLine.hasOption("complement")) {
        configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
      }

      if (commandLine.hasOption(AbstractLTL2LDBAModule.symmetric().getOpt())) {
        configuration.add(SYMMETRIC);
      }

      configuration.add(COMPRESS_COLOURS);

      return Transformers.fromFunction(LabelledFormula.class,
        new LTL2DPAFunction(environment, configuration));
    }
  );

  private LTL2DPAModule() {}

  private static Options options() {
    return new Options()
      .addOption(null, "complement", false,
        "Compute the automaton also for the negation and return the smaller.")
      .addOptionGroup(AbstractLTL2LDBAModule.getOptionGroup());
  }

  // TODO: add Rabinizer based constructions here.
  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
