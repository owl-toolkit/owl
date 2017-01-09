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

package translations.ltl2ldba;

import omega_automaton.acceptance.BuchiAcceptance;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import translations.AbstractSizeRegressionTest;
import translations.Optimisation;
import translations.ldba.LimitDeterministicAutomaton;
import translations.ltl2ldba.ng.Ltl2LdbaNg;
import translations.ltl2ldba.ng.AcceptingComponent;
import translations.ltl2ldba.ng.RecurringObligations2;

import java.util.EnumSet;

@RunWith(Parameterized.class)
public class LdbaNGSizeRegressionTest extends AbstractSizeRegressionTest<LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State, RecurringObligations2>, AcceptingComponent>> {

    public LdbaNGSizeRegressionTest(FormulaGroup selectedClass) {
        super(selectedClass, new Ltl2LdbaNg(EnumSet.allOf(Optimisation.class)));
    }

    @Override
    protected int getSize(LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State, RecurringObligations2>, AcceptingComponent> automaton) {
        return automaton.size();
    }

    @Override
    protected int getAccSize(LimitDeterministicAutomaton<InitialComponentState, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State, RecurringObligations2>, AcceptingComponent> automaton) {
        return automaton.getAcceptingComponent().getAcceptance().getAcceptanceSets();
    }

    @Override
    protected int[] getExpectedSize(FormulaGroup t) {
        switch (t) {
            case FG:
                return new int[]{3, 4, 5, 6, 4, 4};

            case VOLATILE:
                return new int[]{3, 3, 5, 4};

            case ROUND_ROBIN:
                return new int[]{2, 4, 6, 8, 10, 9};

            case REACH:
                return new int[]{3, 3, 112};

            case CONJUNCTION:
                return new int[]{1, 2};

            case IMMEDIATE:
                return new int[]{1, 3};

            case DISJUNCTION:
                return new int[]{2, 4};

            case MIXED:
                return new int[]{4, 4, 6, 7, 9, 21, 5, 4, 6, 8};

            case FG_UNSTABLE:
                return new int[]{3, 5, 5, 5, 4, 3};

            case ORDERINGS:
                return new int[]{9, 14, 3, 4};

            case G_DISJUNCTION:
                return new int[]{35, 16, 73};

            default:
                return new int[0];
        }
    }

    @Override
    protected int[] getExpectedAccSize(FormulaGroup t) {
        return new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    }
}
