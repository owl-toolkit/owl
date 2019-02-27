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

package owl.translations;

import owl.game.GameViews;
import owl.game.algorithms.ParityGameSolver;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.ltl2dpa.LTL2DPAModule;

public final class Synthesis {
  private Synthesis() {}

  public static void main(String... args) {
    @SuppressWarnings("SpellCheckingInspection")
    PartialModuleConfiguration builder = PartialModuleConfiguration.builder("synth")
      .reader(InputReaders.LTL)
      .addTransformer(Transformers.LTL_SIMPLIFIER)
      .addTransformer(LTL2DPAModule.INSTANCE)
      .addTransformer(Transformers.MINIMIZER)
      .addTransformer(GameViews.AUTOMATON_TO_GAME_CLI)
      .addTransformer(ParityGameSolver.ZIELONKA_SOLVER)
      .writer(OutputWriters.TO_STRING) // we need an AIG writer here
      .build();
    PartialConfigurationParser.run(args, builder);
  }
}
