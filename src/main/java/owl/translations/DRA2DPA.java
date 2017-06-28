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

import static com.google.common.base.Preconditions.checkArgument;

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
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.translations.dra2dpa.IARFactory;

public final class DRA2DPA extends AbstractCommandLineTool<Automaton<HoaState, RabinAcceptance>> {
  // TODO This is very close to optimal but not quite!

  public static void main(String... args) {
    new DRA2DPA().execute(new ArrayDeque<>(Arrays.asList(args)));
  }

  @Override
  protected Function<Automaton<HoaState, RabinAcceptance>, ? extends HoaPrintable>
  getTranslation(EnumSet<Optimisation> optimisations) {
    return IARFactory.translator();
  }

  @Override
  protected Collection<CommandLineInput<Automaton<HoaState, RabinAcceptance>>>
  parseInput(InputStream stream) throws ParseException {
    List<Automaton<HoaState, ?>> automata = AutomatonReader.readHoaCollection(stream);
    List<CommandLineInput<Automaton<HoaState, RabinAcceptance>>> inputs = new ArrayList<>();

    for (Automaton<HoaState, ?> automaton : automata) {
      checkArgument(automaton.getAcceptance() instanceof RabinAcceptance);
      //noinspection unchecked
      inputs.add(new CommandLineInput<>((Automaton<HoaState, RabinAcceptance>) automaton,
        automaton.getVariables()));
    }

    return inputs;
  }
}
