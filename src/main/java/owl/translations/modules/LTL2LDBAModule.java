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
import java.util.List;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.optimizations.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;

public final class LTL2LDBAModule {
  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2ldba",
    "Translate LTL to limit-deterministic Büchi automata.",
    AbstractLTL2LDBAModule.options(),
    (commandLine, environment) -> {
      if (commandLine.hasOption(AbstractLTL2LDBAModule.symmetric().getOpt())) {
        return Transformers.fromFunction(LabelledFormula.class,
          SymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
            ::applyWithShortcuts);
      } else {
        return Transformers.fromFunction(LabelledFormula.class,
          AsymmetricLDBAConstruction.of(environment, BuchiAcceptance.class)
            .andThen(AnnotatedLDBA::copyAsMutable));
      }
    }
  );

  private LTL2LDBAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
