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

import static owl.translations.LtlTranslationRepository.LtlToDraTranslation.EKS16;
import static owl.translations.LtlTranslationRepository.LtlToDraTranslation.EKS20;
import static owl.translations.LtlTranslationRepository.LtlToDraTranslation.SE20;
import static owl.translations.LtlTranslationRepository.Option;
import static owl.translations.LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS;
import static owl.translations.LtlTranslationRepository.Option.X_DRA_NORMAL_FORM_USE_DUAL;
import static owl.translations.modules.AbstractLTL2DRAModule.options;
import static owl.translations.modules.AbstractLTL2DRAModule.parseTranslator;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

// Split into subcommand and function.
public final class LTL2DRAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2dra",
    "Translate LTL to deterministic Rabin automata using either "
      + "a symmetric construction (default) based on a unified approach using the Master Theorem or"
      + " an asymmetric construction, also known as the \"Rabinizer construction\".",
    options(),
    (commandLine, environment) -> {
      boolean usePortfolio = AbstractLTL2PortfolioModule.usePortfolio(commandLine);
      Function<LabelledFormula, Automaton<?, ? extends RabinAcceptance>> result;

      switch (parseTranslator(commandLine)) {
        case SYMMETRIC:
          result = EKS20.translation(RabinAcceptance.class,
            usePortfolio
              ? EnumSet.of(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)
              : EnumSet.noneOf(Option.class));
          break;

        case ASYMMETRIC:
          result = EKS16.translation(RabinAcceptance.class,
            usePortfolio
              ? EnumSet.of(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)
              : EnumSet.noneOf(Option.class));
          break;

        case NORMAL_FORM:
          result = SE20.translation(RabinAcceptance.class,
            usePortfolio
              ? EnumSet.of(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS, X_DRA_NORMAL_FORM_USE_DUAL)
              : EnumSet.of(X_DRA_NORMAL_FORM_USE_DUAL));
          break;

        default:
          throw new AssertionError("Unreachable.");
      }

      return OwlModule.LabelledFormulaTransformer.of(result);
    });

  private LTL2DRAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
