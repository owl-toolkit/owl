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
import static com.google.common.base.Preconditions.checkState;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.translations.nba2ldba.NBA2LDBAFunction;

public final class NBA2LDBA extends AbstractCommandLineTool<Automaton<HoaState,
  BuchiAcceptance>> {
  @Nullable
  private List<String> variables = null;

  public static void main(String... args) {
    new NBA2LDBA().execute(new ArrayDeque<>(Arrays.asList(args)));
  }

  @Override
  protected Function<Automaton<HoaState, BuchiAcceptance>, ? extends HoaPrintable>
  getTranslation(EnumSet<Optimisation> optimisations) {
    return new NBA2LDBAFunction<>(optimisations);
  }

  @Override
  protected List<String> getVariables() {
    checkState(variables != null);
    return variables;
  }

  @Override
  protected Automaton<HoaState, BuchiAcceptance> parseInput(InputStream stream)
    throws ParseException {
    Collection<Automaton<HoaState, ?>> automata =
      AutomatonReader.readHoaInput(stream);
    checkArgument(automata.size() == 1);
    Automaton<HoaState, ?> automaton = automata.iterator().next();
    checkArgument(automaton.getAcceptance() instanceof BuchiAcceptance);
    this.variables = automaton.getVariables();
    //noinspection unchecked
    return (Automaton<HoaState, BuchiAcceptance>) automaton;
  }
}
