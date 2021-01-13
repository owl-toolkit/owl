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

import static owl.translations.modules.AbstractLTL2DRAModule.Translation;
import static owl.translations.modules.AbstractLTL2DRAModule.options;
import static owl.translations.modules.AbstractLTL2DRAModule.parseAsymmetric;
import static owl.translations.modules.AbstractLTL2DRAModule.parseTranslator;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.degeneralization.RabinDegeneralization;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.Environment;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModule.Transformer;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.ltl2dra.NormalformDRAConstruction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;

// Split into subcommand and function.
public final class LTL2DRAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2dra",
    "Translate LTL to deterministic Rabin automata using either "
      + "a symmetric construction (default) based on a unified approach using the Master Theorem or"
      + " an asymmetric construction, also known as the \"Rabinizer construction\".",
    options(),
    (commandLine, environment) -> {
      var translator = parseTranslator(commandLine);
      boolean usePortfolio = AbstractLTL2PortfolioModule.usePortfolio(commandLine);
      RabinizerConfiguration configuration = parseAsymmetric(commandLine);
      return OwlModule.LabelledFormulaTransformer
        .of(translation(environment, translator, usePortfolio, configuration, true));
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

  public static Function<LabelledFormula, Automaton<?, RabinAcceptance>>
    translation(Environment environment, Translation translation, boolean usePortfolio,
    @Nullable RabinizerConfiguration configuration, boolean dual) {

    Function<LabelledFormula, Automaton<?, RabinAcceptance>> translator;

    switch (translation) {
      case SYMMETRIC:
        translator = SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true)::apply;
        break;

      case ASYMMETRIC:
        assert configuration != null;
        translator = labelledFormula ->
          RabinDegeneralization.degeneralize(
            AcceptanceOptimizations.optimize(
              RabinizerBuilder.build(labelledFormula, environment, configuration)));
        break;

      case NORMAL_FORM:
        translator = NormalformDRAConstruction.of(environment, RabinAcceptance.class, dual);
        break;

      default:
        throw new AssertionError("Unreachable.");
    }

    DeterministicConstructionsPortfolio<RabinAcceptance> portfolio = usePortfolio
      ? new DeterministicConstructionsPortfolio<>(RabinAcceptance.class, environment)
      : null;

    return labelledFormula -> {
      if (portfolio != null) {
        var automaton = portfolio.apply(labelledFormula);

        if (automaton.isPresent()) {
          return automaton.orElseThrow();
        }
      }

      return translator.apply(labelledFormula);
    };
  }
}
