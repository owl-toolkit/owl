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

import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import translations.AbstractSizeRegressionTest;
import translations.ldba.LimitDeterministicAutomaton;

@RunWith(Parameterized.class)
public class LDGBASizeRegressionTest extends AbstractSizeRegressionTest<LimitDeterministicAutomaton<InitialComponent.State, GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<GeneralisedAcceptingComponent.State>, GeneralisedAcceptingComponent>> {

    public LDGBASizeRegressionTest(FormulaGroup selectedClass) {
        super(selectedClass, new LTL2LDGBA());
    }

    @Override
    protected int getAccSize(LimitDeterministicAutomaton<InitialComponent.State, GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<GeneralisedAcceptingComponent.State>, GeneralisedAcceptingComponent> automaton) {
        return automaton.getAcceptingComponent().getAcceptance().getSize();
    }

    @Override
    protected int getSize(LimitDeterministicAutomaton<InitialComponent.State, GeneralisedAcceptingComponent.State, GeneralisedBuchiAcceptance, InitialComponent<GeneralisedAcceptingComponent.State>, GeneralisedAcceptingComponent> automaton) {
        return automaton.size();
    }

    @Override
    protected int[] getExpectedSize(FormulaGroup t) {
        switch (t) {
            case FG:
                return new int[]{3, 3, 4, 4, 3, 3};

            case VOLATILE:
                return new int[]{3, 3, 5, 4};

            case ROUND_ROBIN:
                return new int[]{2, 3, 4, 5, 6};

            case REACH:
                return new int[]{2, 3};

            case CONJUNCTION:
                return new int[]{1, 2};

            case IMMEDIATE:
                return new int[]{1, 3};

            case DISJUNCTION:
                return new int[]{3, 3};

            case MIXED:
                return new int[]{3, 4, 5, 7, 8, 4, 11, 4, 2 };

            default:
                return new int[0];
        }
    }

    @Override
    protected int[] getExpectedAccSize(FormulaGroup t) {
        switch (t) {
            case FG:
                return new int[]{1, 2, 1, 2, 2, 1};

            default:
                return new int[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        }
    }
}
