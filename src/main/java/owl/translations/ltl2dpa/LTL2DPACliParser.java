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

package owl.translations.ltl2dpa;

import static owl.run.modules.OwlModuleParser.TransformerParser;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLETE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GUESS_F;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;

import java.util.EnumSet;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.translations.ltl2dpa.LTL2DPAFunction.Configuration;
import owl.translations.ltl2ldba.AbstractLTL2LDBAModule;

public final class LTL2DPACliParser implements TransformerParser {
  public static final LTL2DPACliParser INSTANCE = new LTL2DPACliParser();

  private LTL2DPACliParser() {}

  @Override
  public String getKey() {
    return "ltl2dpa";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to deterministic parity automata, using an LDBA construction";
  }

  @Override
  public Options getOptions() {
    return new Options()
      .addOption(null, "complement", false,
        "Compute the automaton also for the negation and return the smaller.")
      .addOption("c", "complete", false,
        "Output a complete automaton")
      .addOption(AbstractLTL2LDBAModule.guessF())
      .addOption(AbstractLTL2LDBAModule.simple());
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    EnumSet<Configuration> configuration;

    if (commandLine.hasOption(AbstractLTL2LDBAModule.simple().getOpt())) {
      configuration = EnumSet.noneOf(Configuration.class);
    } else {
      configuration = EnumSet.of(OPTIMISE_INITIAL_STATE);
    }

    if (commandLine.hasOption("complement")) {
      configuration.add(COMPLEMENT_CONSTRUCTION);
    }

    if (commandLine.hasOption("complete")) {
      configuration.add(COMPLETE);
    }

    if (commandLine.hasOption(AbstractLTL2LDBAModule.guessF().getOpt())) {
      configuration.add(GUESS_F);
    }

    configuration.add(COMPRESS_COLOURS);

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      new LTL2DPAFunction(environment, configuration));
  }
}
