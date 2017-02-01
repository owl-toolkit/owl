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

package owl.translations.ltl2ldba;

import java.util.EnumSet;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import owl.translations.AbstractSizeRegressionTest;
import owl.translations.Optimisation;
import owl.translations.ldba.LimitDeterministicAutomaton;
import owl.translations.ltl2ldba.ng.Ltl2LdgbaNg;
import owl.translations.ltl2ldba.ng.RecurringObligations2;

@RunWith(Parameterized.class)
public class LdgbaNGSizeRegressionTest extends
  AbstractSizeRegressionTest<LimitDeterministicAutomaton<InitialComponentState, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, RecurringObligations2>, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent>> {

  public LdgbaNGSizeRegressionTest(FormulaGroup selectedClass) {
    super(selectedClass, new Ltl2LdgbaNg(EnumSet.allOf(Optimisation.class)));
  }

  @Override
  protected int getAccSize(
    LimitDeterministicAutomaton<InitialComponentState, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, RecurringObligations2>, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent> automaton) {
    return automaton.getAcceptingComponent().getAcceptance().getAcceptanceSets();
  }

  @Override
  protected int[] getExpectedAccSize(FormulaGroup t) {
    switch (t) {
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
  protected int[] getExpectedSize(FormulaGroup t) {
    switch (t) {
      case FG:
        return new int[] {3, 3, 5, 5, 3, 4};

      case VOLATILE:
        return new int[] {3, 3, 5, 4};

      case ROUND_ROBIN:
        return new int[] {2, 4, 6, 8, 10, 1};

      case REACH:
        return new int[] {3, 3, 112};

      case CONJUNCTION:
        return new int[] {1, 2};

      case IMMEDIATE:
        return new int[] {1, 3};

      case DISJUNCTION:
        return new int[] {2, 4};

      case MIXED:
        return new int[] {4, 4, 6, 7, 9, 18, 5, 4, 8, 8};

      case FG_UNSTABLE:
        return new int[] {3, 5, 5, 5, 4, 3};

      case ORDERINGS:
        return new int[] {9, 9, 3, 4};

      case G_DISJUNCTION:
        return new int[] {30, 16, 41};

      default:
        return new int[0];
    }
  }

  @Override
  protected int getSize(
    LimitDeterministicAutomaton<InitialComponentState, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent.State, RecurringObligations2>, owl.translations.ltl2ldba.ng.GeneralisedAcceptingComponent> automaton) {
    return automaton.size();
  }
}
