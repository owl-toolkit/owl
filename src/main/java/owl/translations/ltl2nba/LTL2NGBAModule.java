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

package owl.translations.ltl2nba;

import org.apache.commons.cli.CommandLine;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.ltl.LabelledFormula;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class LTL2NGBAModule implements OwlModuleParser.TransformerParser {
  public static final LTL2NGBAModule INSTANCE = new LTL2NGBAModule();

  private LTL2NGBAModule() {}

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
    return LTL2NGBAModule::instance;
  }

  private static Transformer.Instance instance(Environment environment) {
    return Transformers.instanceFromFunction(LabelledFormula.class,
      SymmetricNBAConstruction.of(environment, GeneralizedBuchiAcceptance.class));
  }

  @Override
  public String getKey() {
    return "ltl2ngba";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to non-deterministic generalized-Büchi automata";
  }
}
