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

package ltl.simplifier;

import ltl.Formula;
import ltl.visitors.Visitor;

public final class Simplifier {

    public static final int ITERATIONS = 3;

    private static final Visitor<Formula> MODAL_SIMPLIFIER = new ModalSimplifier();
    private static final Visitor<XFormula> PULLUP_X = new PullupXVisitor();
    private static final Visitor<Formula> AGGRESSIVE_SIMPLIFIER = new AggressiveSimplifier();

    private Simplifier() {
    }

    public static Formula simplify(Formula formula, Strategy strategy) {
        switch (strategy) {
            case PULLUP_X:
                return formula.accept(PULLUP_X).toFormula();

            case MODAL_EXT:
                Formula step0 = formula;

                for (int i = 0; i < ITERATIONS; i++) {
                    Formula step2 = step0.accept(PULLUP_X).toFormula();
                    Formula step3 = step2.accept(MODAL_SIMPLIFIER);

                    if (step0.equals(step3)) {
                        return step0;
                    }

                    step0 = step3;
                }

                return step0;

            case MODAL:
                return formula.accept(Simplifier.MODAL_SIMPLIFIER);

            case AGGRESSIVELY:
                return formula.accept(Simplifier.AGGRESSIVE_SIMPLIFIER);

            case NONE:
                return formula;

            default:
                throw new AssertionError();
        }
    }

    public enum Strategy {
        NONE, MODAL, PULLUP_X, MODAL_EXT, AGGRESSIVELY
    }
}
