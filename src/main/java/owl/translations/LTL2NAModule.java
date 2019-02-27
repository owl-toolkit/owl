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

package owl.translations;

import static owl.translations.LTL2NAFunction.Constructions.BUCHI;
import static owl.translations.LTL2NAFunction.Constructions.CO_SAFETY;
import static owl.translations.LTL2NAFunction.Constructions.GENERALIZED_BUCHI;
import static owl.translations.LTL2NAFunction.Constructions.SAFETY;

import java.util.EnumSet;
import owl.ltl.LabelledFormula;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class LTL2NAModule {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("ltl2na")
    .description("Translates LTL to some non-deterministic automaton")
    .parser(settings -> environment -> {
      LTL2NAFunction function = new LTL2NAFunction(environment,
        EnumSet.of(SAFETY, CO_SAFETY, BUCHI, GENERALIZED_BUCHI));
      return Transformers.instanceFromFunction(LabelledFormula.class, function::apply);
    })
    .build();

  private LTL2NAModule() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("ltl2na")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(CLI)
      .addTransformer(Transformers.MINIMIZER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
