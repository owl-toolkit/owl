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

package owl.translations.ltl2ldba.breakpointfree;

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
public class GeneralisedSizeRegressionTest extends
  AbstractSizeRegressionTest<LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations>> {

  public GeneralisedSizeRegressionTest(FormulaGroup selectedClass) {
    super(selectedClass, LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(getOpt()));
  }

  private static EnumSet<Optimisation> getOpt() {
    return EnumSet.allOf(Optimisation.class);
  }

  @Override
  protected int getAccSize(
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointFreeState,
      GeneralizedBuchiAcceptance, FGObligations> automaton) {
    return automaton.getAcceptingComponent().getAcceptance().getAcceptanceSets();
  }

  @Override
  protected int[] getExpectedAccSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case MIXED:
        return new int[] {1, 1, 1, 1, 1, 2, 1, 1, 2, 1};

      case ROUND_ROBIN:
        return new int[] {1, 1, 1, 1, 1, 9};

      case FG:
        return new int[] {1, 2, 1, 2, 2, 1};

      case ORDERINGS:
        return new int[] {1, 3};

      case G_DISJUNCTION:
        return new int[] {3, 16, 73};

      default:
        return new int[] {1, 1, 1, 1, 1, 1, 1, 1, 2, 1};
    }
  }

  @Override
  protected int[] getExpectedSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case FG:
        return new int[] {3, 3, 5, 5, 3, 4};

      case VOLATILE:
        return new int[] {3, 3, 5, 4};

      case ROUND_ROBIN:
        return new int[] {2, 4, 6, 8, 10, 1};

      case REACH:
        return new int[] {3, 3, 314};

      case CONJUNCTION:
        return new int[] {1, 2};

      case IMMEDIATE:
        return new int[] {1, 3};

      case DISJUNCTION:
        return new int[] {3, 4};

      case MIXED:
        return new int[] {4, 4, 9, 10, 12, 18, 8, 4, 9, 17}; // was 4, 4, 6, 7, 7 ... 8

      case FG_UNSTABLE:
        return new int[] {4, 5, 6, 6, 4, 3};

      case ORDERINGS:
        return new int[] {9, 9, 3, 4};

      case G_DISJUNCTION:
        return new int[] {40, 18, 57};

      default:
        return new int[0];
    }
  }

  @Override
  protected int getSize(
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointFreeState,
      GeneralizedBuchiAcceptance, FGObligations> automaton) {
    return automaton.size();
  }
}
