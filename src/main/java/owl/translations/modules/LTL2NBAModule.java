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
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
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
import owl.translations.canonical.NonDeterministicConstructionsPortfolio;
import owl.translations.ltl2nba.SymmetricNBAConstruction;

public final class LTL2NBAModule {
  public static final OwlModule<Transformer> MODULE = OwlModule.of(
    "ltl2nba",
    "Translate LTL to non-deterministic Büchi automata. "
      + "The construction is based on the symmetric approach from [EKS: LICS'18].",
    AbstractLTL2PortfolioModule.disablePortfolio(),
    (commandLine, environment) -> OwlModule.LabelledFormulaTransformer.of(
      translation(environment, AbstractLTL2PortfolioModule.usePortfolio(commandLine)))
  );

  private LTL2NBAModule() {}

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  public static Function<LabelledFormula, Automaton<?, BuchiAcceptance>>
    translation(Environment environment, boolean usePortfolio) {

    var construction = SymmetricNBAConstruction.of(environment, BuchiAcceptance.class);
    var portfolio = usePortfolio
      ? new NonDeterministicConstructionsPortfolio<>(BuchiAcceptance.class, environment)
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
