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

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import ltl.Formula;
import omega_automaton.output.HOAPrintable;
import owl.translations.ltl2dpa.Ltl2Dpa;

public class LTL2DPA extends AbstractLTLCommandLineTool {

  private final boolean parallel;

  private LTL2DPA(boolean parallel) {
    this.parallel = parallel;
  }

  public static void main(String... argsArray) throws Exception {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new LTL2DPA(args.remove("--parallel")).execute(args);
  }

  @Override
  protected Function<Formula, ? extends HOAPrintable> getTranslation(
    EnumSet<Optimisation> optimisations) {
    optimisations.add(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);

    if (parallel) {
      optimisations.add(Optimisation.PARALLEL);
    } else {
      optimisations.remove(Optimisation.PARALLEL);
    }

    return new Ltl2Dpa(optimisations);
  }
}
