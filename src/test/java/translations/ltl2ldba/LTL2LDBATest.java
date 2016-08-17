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
import translations.ldba.LimitDeterministicAutomaton;

@RunWith(Parameterized.class)
public class LTL2LDBATest extends AbstractSizeRegressionTest<LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent>> {

    public LTL2LDBATest(FormulaGroup selectedClass) {
        super(selectedClass, new LTL2LDBA());
    }

    @Override
    protected int getSize(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> automaton) {
        return automaton.size();
    }

    @Override
    protected int getAccSize(LimitDeterministicAutomaton<InitialComponent.State, AcceptingComponent.State, BuchiAcceptance, InitialComponent<AcceptingComponent.State>, AcceptingComponent> automaton) {
        return automaton.getAcceptingComponent().getAcceptance().getAcceptanceSets();
    }

    @Override
    protected int[] getExpectedSize(FormulaGroup t) {
        switch (t) {
            case FG:
                return new int[]{3, 4, 4, 5, 4, 5, 4, 3};

            case VOLATILE:
                return new int[]{3, 3, 5, 4};

            case ROUND_ROBIN:
                return new int[]{2, 3, 4, 5, 6};

            case REACH:
            case CONJUNCTION:
                return new int[]{2};

            case IMMEDIATE:

                return new int[]{1};

            case DISJUNCTION:
                return new int[]{3};

            case MIXED:
                return new int[]{3, 4, 5, 7, 8, 4, 14 };

            default:
                return new int[0];
        }
    }

    @Override
    protected int[] getExpectedAccSize(FormulaGroup t) {
        return new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    }
}
