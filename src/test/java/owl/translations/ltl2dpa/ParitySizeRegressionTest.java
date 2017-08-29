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

package owl.translations.ltl2dpa;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.translations.AbstractSizeRegressionTest;

@RunWith(Parameterized.class)
public class ParitySizeRegressionTest
  extends AbstractSizeRegressionTest<MutableAutomaton<?, ParityAcceptance>> {

  public ParitySizeRegressionTest(FormulaGroup selectedClass) {
    super(selectedClass, new LTL2DPAFunction());
  }

  @Override
  protected int getAccSize(MutableAutomaton<?, ParityAcceptance> automaton) {
    return automaton.getAcceptance().getAcceptanceSets();
  }

  @Override
  protected int[] getExpectedAccSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case VOLATILE:
        return new int[] {2, 2, 2, 2};

      case ROUND_ROBIN:
        return new int[] {2, 2, 2, 2, 2, 3};

      case REACH:
      case DISJUNCTION:
        return new int[] {2, 2, 2};

      case IMMEDIATE:
        return new int[] {2, 2};

      case CONJUNCTION:
        return new int[] {2, 2};

      case FG:
        return new int[] {4, 4, 4, 4, 4, 5};

      case MIXED:
        return new int[] {4, 3, 2, 2, 4, 3, 2, 3, 3, 7};

      case FG_UNSTABLE:
        return new int[] {3, 4, 2, 4, 4, 5};

      case ORDERINGS:
        return new int[] {2, 2};

      case G_DISJUNCTION:
        return new int[] {3, 2, 7};

      default:
        return new int[0];
    }
  }

  @Override
  protected int[] getExpectedSize(FormulaGroup formulaGroup) {
    switch (formulaGroup) {
      case FG:
        return new int[] {1, 2, 2, 4, 2, 2, 2, 2};

      case VOLATILE:
        return new int[] {2, 1, 4, 1};

      case ROUND_ROBIN:
        return new int[] {1, 2, 3, 4, 5, 9};

      case REACH:
        return new int[] {2, 3, 314};

      case DISJUNCTION:
        return new int[] {3, 2};

      case IMMEDIATE:
        return new int[] {1, 3};

      case CONJUNCTION:
        return new int[] {1, 1};

      case MIXED:
        return new int[] {2, 4, 4, 4, 5, 4, 3, 2, 6, 19};

      case FG_UNSTABLE: // The order of the rankings is unstable...
        return new int[] {3, 2, 3, 4, 2, 2, 2, 2};

      case ORDERINGS:
        return new int[] {3, 1};

      case G_DISJUNCTION:
        return new int[] {212, 18, 346};

      default:
        return new int[0];
    }
  }

  @Override
  protected int getSize(MutableAutomaton<?, ParityAcceptance> automaton) {
    return automaton.stateCount();
  }
}
