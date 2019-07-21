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
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.ltl.LabelledFormula;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;

public final class LTL2LDGBAModule extends AbstractLTL2LDBAModule {
  public static final LTL2LDGBAModule INSTANCE = new LTL2LDGBAModule();

  private LTL2LDGBAModule() {}

  @Override
  public Transformer parse(CommandLine commandLine) {
    if (commandLine.hasOption(symmetric().getOpt())) {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        SymmetricLDBAConstruction.of(environment, GeneralizedBuchiAcceptance.class)
          ::applyWithShortcuts);
    } else {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        AsymmetricLDBAConstruction.of(environment, GeneralizedBuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable));
    }
  }

  @Override
  public String getKey() {
    return "ltl2ldgba";
  }

  @Override
  public String getDescription() {
    return "Translate LTL to limit-deterministic generalized Büchi automata.";
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder(INSTANCE.getKey())
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .addTransformer(Transformers.ACCEPTANCE_OPTIMIZATION_TRANSFORMER)
      .writer(OutputWriters.HOA)
      .build());
  }
}
