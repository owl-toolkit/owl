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

import java.io.IOException;
import org.apache.commons.cli.CommandLine;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.ltl.LabelledFormula;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;

public final class LTL2DGRAModule extends AbstractLTL2DRAModule {
  public static final LTL2DGRAModule INSTANCE = new LTL2DGRAModule();

  private LTL2DGRAModule() {}

  @Override
  public String getKey() {
    return "ltl2dgra";
  }

  @Override
  public String getDescription() {
    return "Translate LTL to deterministic generalized Rabin automata using either "
      + "a symmetric construction (default) based on a unified approach using the Master Theorem or"
      + " an asymmetric construction, also known as the \"Rabinizer construction\".";
  }

  @Override
  public Transformer parse(CommandLine commandLine) {
    RabinizerConfiguration configuration = parseAsymmetric(commandLine);

    if (configuration == null) {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true));
    } else {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        formula -> RabinizerBuilder.build(formula, environment, configuration));
    }
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2dgra")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .addTransformer(Transformers.ACCEPTANCE_OPTIMIZATION_TRANSFORMER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
