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
import owl.automaton.Automaton;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.output.HoaPrintable;
import owl.ltl.LabelledFormula;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

public final class LTL2DA extends AbstractLtlCommandLineTool {
  private final boolean parallel;

  private LTL2DA(boolean parallel) {
    this.parallel = parallel;
  }

  public static void main(String... argsArray) {
    Deque<String> args = new ArrayDeque<>(Arrays.asList(argsArray));
    new LTL2DA(args.remove("--parallel")).execute(args);
  }

  private static HoaPrintable translate(LabelledFormula formula,
    EnumSet<Optimisation> optimisations) {
    optimisations.remove(Optimisation.COMPLETE);
    LTL2DPAFunction ltl2Dpa = new LTL2DPAFunction(optimisations);
    LimitDeterministicAutomaton<?, ?, ?, ?> ldba = LTL2LDBAFunction
      .createGeneralizedBreakpointLDBABuilder(optimisations).apply(formula);
    Automaton<?, ?> automaton = ltl2Dpa.apply(formula);

    if (ldba.isDeterministic()
      && ldba.getAcceptingComponent().stateCount() <= automaton.stateCount()) {
      automaton = ldba.getAcceptingComponent();
    }

    return automaton;
  }

  @Override
  protected Function<LabelledFormula, ? extends HoaPrintable>
  getTranslation(EnumSet<Optimisation> optimisations) {

    if (parallel) {
      optimisations.add(Optimisation.PARALLEL);
    } else {
      optimisations.remove(Optimisation.PARALLEL);
    }

    return x -> translate(x, optimisations);
  }
}
