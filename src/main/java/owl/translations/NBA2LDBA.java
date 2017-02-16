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

import com.google.common.collect.Iterables;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.StoredBuchiAutomaton;
import owl.automaton.output.HoaPrintable;

public class NBA2LDBA extends AbstractCommandLineTool<StoredBuchiAutomaton> {
  private List<String> variables;

  @SuppressWarnings("ProhibitedExceptionDeclared")
  public static void main(String... args) throws Exception { // NOPMD
    new NBA2LDBA().execute(new ArrayDeque<>(Arrays.asList(args)));
  }

  @Override
  protected List<String> getVariables() {
    return variables;
  }

  @Override
  protected Function<StoredBuchiAutomaton, ? extends HoaPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {
    return new owl.translations.nba2ldba.Nba2Ldba(optimisations);
  }

  @Override
  protected StoredBuchiAutomaton parseInput(InputStream stream) throws ParseException {
    StoredBuchiAutomaton.Builder builder = new StoredBuchiAutomaton.Builder();
    HOAFParser.parseHOA(stream, builder);
    StoredBuchiAutomaton nba = Iterables.getOnlyElement(builder.getAutomata());
    variables = nba.getVariables();
    return nba;
  }
}
