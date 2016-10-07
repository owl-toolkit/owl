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

package ltl;

import ltl.visitors.BinaryVisitor;
import ltl.visitors.IntVisitor;
import ltl.visitors.Visitor;
import ltl.visitors.VoidVisitor;

import java.util.BitSet;

public interface Formula {

    void accept(VoidVisitor visitor);

    int accept(IntVisitor visitor);

    <R> R accept(Visitor<R> visitor);

    <A, B> A accept(BinaryVisitor<A, B> visitor, B f);

    // Temporal Properties of an LTL Formula
    boolean isPureEventual();

    boolean isPureUniversal();

    boolean isSuspendable();

    /**
     * Syntactically negate this formula.
     *
     * @return The negation of this formula in NNF.
     */
    Formula not();

    /**
     * Do a single temporal step. This means that one layer of X-operators is
     * removed and literals are replaced by their valuations.
     *
     * @param valuation
     * @return
     */
    Formula temporalStep(BitSet valuation);

    Formula unfold();

    /**
     * Short-cut operation to avoid intermediate construction of formula ASTs.
     * @param valuation
     * @return
     */
    Formula unfoldTemporalStep(BitSet valuation);

    /**
     * Short-cut operation to avoid intermediate construction of formula ASTs.
     * @param valuation
     * @return
     */
    Formula temporalStepUnfold(BitSet valuation);

    /**
     * Compute the heigt of the syntax tree of this formula
     *
     * @return
     */
    int height();

    boolean isSubformula(Formula formula);

    default boolean containsSubformula(Iterable<? extends Formula> formulas) {
        for (Formula formula : formulas) {
            if (isSubformula(formula)) {
                return true;
            }
        }

        return false;
    }
}
