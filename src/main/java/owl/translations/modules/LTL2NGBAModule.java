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

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.LtlTranslationRepository;

public final class LTL2NGBAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2ngba",
    "Translate LTL to non-deterministic generalized-Büchi automata. "
      + "The construction is based on the symmetric approach from [EKS: LICS'18].",
    AbstractLTL2PortfolioModule.disablePortfolio(),
    (commandLine, environment) -> OwlModule.LabelledFormulaTransformer.of(
      LtlTranslationRepository.LtlToNbaTranslation.EKS20
        .translation(GeneralizedBuchiAcceptance.class,
          AbstractLTL2PortfolioModule.usePortfolio(commandLine)
            ? EnumSet.of(LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)
            : EnumSet.noneOf(LtlTranslationRepository.Option.class)))
  );

  private LTL2NGBAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
