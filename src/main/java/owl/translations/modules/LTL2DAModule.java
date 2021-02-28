/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

import static owl.run.modules.OwlModule.Transformer;
import static owl.translations.LtlTranslationRepository.BranchingMode.DETERMINISTIC;

import java.io.IOException;
import java.util.List;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.LtlTranslationRepository;

public final class LTL2DAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2da",
    "Translate LTL to a (heuristically chosen) small deterministic automaton.",
    (commandLine, environment) ->
      OwlModule.LabelledFormulaTransformer.of(
        LtlTranslationRepository.smallestAutomaton(
          DETERMINISTIC, EmersonLeiAcceptance.class)));

  private LTL2DAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
