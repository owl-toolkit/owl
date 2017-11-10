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
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import jhoafparser.parser.generated.ParseException;
import owl.automaton.Automaton;
import owl.automaton.AutomatonReader;
import owl.automaton.AutomatonReader.HoaState;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.output.HoaPrintable;
import owl.factories.jbdd.JBddSupplier;
import owl.translations.nba2dpa.NBA2DPAFunction;

public final class NBA2DPA extends AbstractCommandLineTool<Automaton<HoaState,
  GeneralizedBuchiAcceptance>> {
  public static void main(String... args) {
    new NBA2DPA().execute(new ArrayDeque<>(Arrays.asList(args)));
  }

  @Override
  protected Function<Automaton<HoaState, GeneralizedBuchiAcceptance>, ? extends HoaPrintable>
  getTranslation(EnumSet<Optimisation> optimisations) {
    return new NBA2DPAFunction<>();
  }

  @SuppressWarnings("unchecked")
  @Override
  protected Collection<Automaton<HoaState, GeneralizedBuchiAcceptance>>
  parseInput(InputStream stream) throws ParseException {
    return AutomatonReader.readHoaCollection(stream, JBddSupplier.async()).stream()
      .map(automaton -> (Automaton<HoaState, GeneralizedBuchiAcceptance>) automaton)
      .collect(Collectors.toList());
  }
}
