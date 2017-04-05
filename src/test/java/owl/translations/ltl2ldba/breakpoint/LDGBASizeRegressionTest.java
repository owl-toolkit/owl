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

package owl.translations.ltl2ldba.breakpoint;

import java.util.EnumSet;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.ltl.EquivalenceClass;
import owl.translations.AbstractSizeRegressionTest;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.LTL2LDBAFunction;

@RunWith(Parameterized.class)
public class LDGBASizeRegressionTest extends
  AbstractSizeRegressionTest<LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>> {

  public LDGBASizeRegressionTest(FormulaGroup selectedClass) {
    super(selectedClass,
      LTL2LDBAFunction.createGeneralizedBreakpointLDBABuilder(EnumSet.allOf(Optimisation.class)));
  }

  @Override
  protected int getAccSize(
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointState,
      GeneralizedBuchiAcceptance, GObligations> automaton) {
    return automaton.getAcceptingComponent().getAcceptance().size;
  }

  @Override
  protected int[] getExpectedAccSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case MIXED:
        return new int[] {1, 1, 1, 1, 1, 1, 1, 1, 2, 1};

      case ROUND_ROBIN:
        return new int[] {1, 1, 1, 1, 1, 9};

      case FG:
        return new int[] {1, 2, 1, 2, 2, 1};

      case ORDERINGS:
        return new int[] {1, 3};

      default:
        return new int[] {1, 1, 1, 1, 1, 1, 1, 1, 2, 1};
    }
  }

  @Override
  protected int[] getExpectedSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case FG:
        return new int[] {3, 3, 4, 4, 3, 3};

      case VOLATILE:
        return new int[] {3, 3, 5, 4};

      case ROUND_ROBIN:
        return new int[] {2, 3, 4, 5, 6, 1};

      case REACH:
        return new int[] {2, 3, 314};

      case CONJUNCTION:
        return new int[] {1, 2};

      case IMMEDIATE:
        return new int[] {1, 3};

      case DISJUNCTION:
        return new int[] {3, 3};

      case MIXED:
        return new int[] {3, 4, 6, 6, 4, 4, 4, 2, 8, 20};

      case FG_UNSTABLE:
        return new int[] {4, 6, 5, 5, 4, 3};

      case ORDERINGS:
        return new int[] {4, 2, 3, 4};

      case G_DISJUNCTION:
        return new int[] {212, 24, 133};

      default:
        return new int[0];
    }
  }

  @Override
  protected int getSize(
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointState,
      GeneralizedBuchiAcceptance, GObligations> automaton) {
    return automaton.size();
  }
}