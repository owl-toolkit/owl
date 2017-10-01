/*
 * Copyright (C) 2016  (See AUTHORS)
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

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.translations.nba2ldba.NBA2LDBAFunction;

public final class NBA2LDBA extends AbstractCommandLineTool<Automaton<HoaState,
  ? extends OmegaAcceptance>> {
  public static void main(String... args) {
    new NBA2LDBA().execute(new ArrayDeque<>(Arrays.asList(args)));
  }

  @Override
  protected Function<Automaton<HoaState, ? extends OmegaAcceptance>, ? extends HoaPrintable>
  getTranslation(EnumSet<Optimisation> optimisations) {
    return new NBA2LDBAFunction<>(EnumSet.of(Optimisation.REMOVE_EPSILON_TRANSITIONS,
        Optimisation.REMOVE_NON_ACCEPTING_COMPONENTS));
  }

  @Override
  protected Collection<CommandLineInput<Automaton<HoaState, ? extends OmegaAcceptance>>>
  parseInput(InputStream stream) throws ParseException {
    List<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(stream);
    List<CommandLineInput<Automaton<HoaState, ? extends OmegaAcceptance>>> inputs =
      new ArrayList<>();

    for (Automaton<HoaState, ?> automaton : automata) {
      inputs.add(new CommandLineInput<>(automaton, automaton.getVariables()));
    }

    return inputs;
  }
}
