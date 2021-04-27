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

import static owl.translations.LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS;
import static owl.translations.LtlTranslationRepository.Option.X_DPA_USE_COMPLEMENT;

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import org.apache.commons.cli.Options;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.symbolic.SymbolicAutomaton;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.LtlTranslationRepository;
import owl.translations.LtlTranslationRepository.Option;
import owl.translations.ltl2dpa.SymbolicDPAConstruction;

public final class LTL2DPAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2dpa",
    "Translate LTL to deterministic parity automata, using LDBA constructions.",
    options(),
    (commandLine, environment) -> {
      if (commandLine.hasOption("symbolic")) {
        return OwlModule.LabelledFormulaTransformer.of(new SymbolicDPAConstruction()
          .andThen(SymbolicAutomaton::toAutomaton)
        );
      }
      boolean useSymmetric = commandLine.hasOption(AbstractLTL2LDBAModule.symmetric().getOpt());
      boolean useComplement = !commandLine.hasOption("disable-complement");
      boolean usePortfolio = AbstractLTL2PortfolioModule.usePortfolio(commandLine);

      return OwlModule.LabelledFormulaTransformer
        .of(useSymmetric
          ? (useComplement
          ? (usePortfolio
          ? LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
          EnumSet.of(X_DPA_USE_COMPLEMENT, USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS))
          : LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
            EnumSet.of(X_DPA_USE_COMPLEMENT)))
          : (usePortfolio
            ? LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
            EnumSet.of(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS))
            : LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
              EnumSet.noneOf(Option.class))))
          : (useComplement
            ? (usePortfolio
            ? LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
            EnumSet.of(X_DPA_USE_COMPLEMENT, USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS))
            : LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
              EnumSet.of(X_DPA_USE_COMPLEMENT)))
            : (usePortfolio
              ? LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
              EnumSet.of(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS))
              : LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
                EnumSet.noneOf(Option.class)))));
    }
  );

  private LTL2DPAModule() {}

  private static Options options() {
    return new Options()
      .addOptionGroup(AbstractLTL2LDBAModule.getOptionGroup())
      .addOption(null, "disable-complement", false,
        "Disable the parallel computation of a DPA for the negation of the formula. If "
          + "the parallel computation is left not disabled, then two DPAs are computed and the "
          + "smaller (number of states) is returned.")
      .addOption(AbstractLTL2PortfolioModule.disablePortfolio())
      .addOption(null, "symbolic", false,
        "Use a symbolic construction (ignores other options)."
      );
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }
}
