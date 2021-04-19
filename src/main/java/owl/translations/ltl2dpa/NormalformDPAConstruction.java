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

package owl.translations.ltl2dpa;

import static owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.Path;
import static owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.transform;
import static owl.translations.ltl2dela.NormalformDELAConstruction.State;

import java.io.IOException;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Function;
import org.apache.commons.cli.Options;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.AcceptanceTransformation.ExtendedState;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dela.NormalformDELAConstruction;

public final class NormalformDPAConstruction
  implements Function<LabelledFormula, Automaton<ExtendedState<State, Path>, ParityAcceptance>> {

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2dpaNormalform",
    "Translate LTL to DPA using normalforms and zielonka split trees.",
    new Options()
      .addOption(null, "X-lookahead", true,
      "Only used for testing internal implementation."),
    (commandLine, environment) -> {
      OptionalInt lookahead;

      if (commandLine.hasOption("X-lookahead")) {
        String value = commandLine.getOptionValue("X-lookahead");
        int intValue = Integer.parseInt(value);

        if (intValue < -1) {
          throw new IllegalArgumentException();
        }

        lookahead = intValue == -1 ? OptionalInt.empty() : OptionalInt.of(intValue);
      } else {
        lookahead = OptionalInt.empty();
      }

      return OwlModule.LabelledFormulaTransformer.of(
        x -> new NormalformDPAConstruction(lookahead).apply(x));
    });

  private final NormalformDELAConstruction normalformDELAConstruction;

  private final OptionalInt lookahead;

  public NormalformDPAConstruction(OptionalInt lookahead) {
    this.lookahead = lookahead;
    this.normalformDELAConstruction = new NormalformDELAConstruction(lookahead);
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  @Override
  public Automaton<ExtendedState<State, Path>, ParityAcceptance> apply(LabelledFormula formula) {

    var delwConstruction = normalformDELAConstruction.applyConstruction(formula);
    return transform(
      delwConstruction.automaton(),
      lookahead,
      State::inDifferentSccs,
      delwConstruction::alpha,
      delwConstruction::beta);
  }
}
