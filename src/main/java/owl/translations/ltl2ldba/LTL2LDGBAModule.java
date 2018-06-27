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

import org.apache.commons.cli.CommandLine;
import owl.ltl.LabelledFormula;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class LTL2LDGBAModule extends AbstractLTL2LDBAModule {
  public static final LTL2LDGBAModule INSTANCE = new LTL2LDGBAModule();

  private LTL2LDGBAModule() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder(INSTANCE.getKey())
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .writer(OutputWriters.HOA)
      .build());
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    var configuration = configuration(commandLine);

    if (commandLine.hasOption(guessF().getOpt())) {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(environment, configuration));
    } else {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(environment, configuration));
    }
  }

  @Override
  public String getKey() {
    return "ltl2ldgba";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to limit-deterministic generalized Büchi automata";
  }
}