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

import org.apache.commons.cli.CommandLine;
import owl.automaton.acceptance.BuchiAcceptance;
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

public final class LTL2LDBAModule extends AbstractLTL2LDBAModule {
  public static final LTL2LDBAModule INSTANCE = new LTL2LDBAModule();

  private LTL2LDBAModule() {}

  @Override
  public Transformer parse(CommandLine commandLine) {
    if (commandLine.hasOption(symmetric().getOpt())) {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        SymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable));
    } else {
      return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
        AsymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
          .andThen(AnnotatedLDBA::copyAsMutable));
    }
  }

  @Override
  public String getKey() {
    return "ltl2ldba";
  }

  @Override
  public String getDescription() {
    return "Translate LTL to limit-deterministic Büchi automata.";
  }

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder(INSTANCE.getKey())
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(INSTANCE)
      .writer(OutputWriters.HOA)
      .build());
  }
}
