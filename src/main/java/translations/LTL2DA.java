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

package translations;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.function.Function;
import ltl.Formula;
import omega_automaton.Automaton;
import omega_automaton.output.HOAPrintable;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2dpa.Ltl2Dpa;
import translations.ltl2ldba.Ltl2Ldgba;

public class LTL2DA extends AbstractLTLCommandLineTool {

  private final boolean parallel;

  private LTL2DA(boolean parallel) {
    this.parallel = parallel;
  }

  private static HOAPrintable translate(Formula formula, EnumSet<Optimisation> optimisations) {
    Ltl2Ldgba ltl2Ldgba = new Ltl2Ldgba(optimisations);
    Ltl2Dpa ltl2Dpa = new Ltl2Dpa(optimisations);

    LimitDeterministicAutomaton<?, ?, ?, ?, ?> ldba = ltl2Ldgba.apply(formula);
    Automaton<?, ?> automaton = ltl2Dpa.apply(formula);

    if (ldba.isDeterministic() && ldba.getAcceptingComponent().size() <= automaton.size()) {
      automaton = ldba.getAcceptingComponent();
    }

    return automaton;
  }

  @Override
  protected Function<Formula, ? extends HOAPrintable> getTranslation(EnumSet<Optimisation> optimisations) {
    if (parallel) {
      optimisations.add(Optimisation.PARALLEL);
    } else {
      optimisations.remove(Optimisation.PARALLEL);
    }

    return (x) -> (translate(x, optimisations));
  }

  public static void main(String... argsArray) throws Exception {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new LTL2DA(args.remove("--parallel")).execute(args);
  }
}
