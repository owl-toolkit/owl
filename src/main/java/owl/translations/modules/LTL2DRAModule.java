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
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;

public final class LTL2DRAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2dra",
    "Translate LTL to deterministic Rabin automata using either "
      + "a symmetric construction (default) based on a unified approach using the Master Theorem or"
      + " an asymmetric construction, also known as the \"Rabinizer construction\".",
    AbstractLTL2DRAModule.options(),
    (commandLine, environment) -> {
      RabinizerConfiguration configuration = AbstractLTL2DRAModule.parseAsymmetric(commandLine);
      boolean useSymmetric = configuration == null;
      boolean usePortfolio = AbstractLTL2PortfolioModule.usePortfolio(commandLine);
      return OwlModule.LabelledFormulaTransformer
        .of(translation(environment, useSymmetric, usePortfolio, configuration));
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
    translation(Environment environment, boolean useSymmetric, boolean usePortfolio,
    @Nullable RabinizerConfiguration configuration) {

    Function<LabelledFormula, Automaton<?, RabinAcceptance>> construction;

    if (useSymmetric) {
      construction = SymmetricDRAConstruction.of(environment, RabinAcceptance.class, true)::apply;
    } else {
      assert configuration != null;
      construction = labelledFormula ->
        RabinDegeneralization.degeneralize(
          AcceptanceOptimizations.optimize(
            RabinizerBuilder.build(labelledFormula, environment, configuration)));
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

      return construction.apply(labelledFormula);
    };
  }
}
