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

package translations.ltl2parity;

import omega_automaton.Automaton;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import translations.AbstractSizeRegressionTest;

@RunWith(Parameterized.class)
public class LTL2ParityTest extends AbstractSizeRegressionTest<ParityAutomaton<?>> {

    public LTL2ParityTest(FormulaGroup selectedClass) {
        super(selectedClass, new LTL2Parity());
    }

    @Override
    protected int getSize(ParityAutomaton<?> automaton) {
        return automaton.size();
    }

    @Override
    protected int getAccSize(ParityAutomaton<?> automaton) {
        return automaton.getAcceptance().getAcceptanceSets();
    }

    @Override
    protected int[] getExpectedSize(FormulaGroup t) {
        switch (t) {
            case FG:
                return new int[]{1, 2, 2, 4, 3, 2, 2, 2};

            case VOLATILE:
                return new int[]{2, 2, 4, 3};

            case ROUND_ROBIN:
                return new int[]{1, 2, 3, 4, 5};

            case REACH:
            case DISJUNCTION:
                return new int[]{2};

            case IMMEDIATE:
            case CONJUNCTION:
                return new int[]{1};

            case MIXED:
                return new int[]{2, 4, 4, 4, 5, 3, 4};

            default:
                return new int[0];
        }
    }

    @Override
    protected int[] getExpectedAccSize(FormulaGroup t) {
        switch (t) {
            case VOLATILE:
                return new int[]{2, 2, 2, 2};

            case ROUND_ROBIN:
                return new int[]{2, 2, 2, 2, 2};

            case REACH:
            case IMMEDIATE:
            case CONJUNCTION:
            case DISJUNCTION:
                return new int[]{2};

            case FG:
                return new int[]{4, 4, 4, 4, 3, 4, 4, 5};

            case MIXED:
                return new int[]{4, 3, 2, 3, 4, 3, 5};

            default:
                return new int[0];
        }
    }
}
