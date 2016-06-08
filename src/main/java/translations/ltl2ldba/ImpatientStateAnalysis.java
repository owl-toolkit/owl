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

import ltl.*;
import ltl.equivalence.EquivalenceClass;

import java.util.HashSet;
import java.util.Set;

class ImpatientStateAnalysis {

    private static final AnalysisVisitor INSTANCE = new AnalysisVisitor();

    private ImpatientStateAnalysis() {

    }

    public static boolean isImpatientClazz(EquivalenceClass clazz) {
        if (clazz.isTrue() || clazz.isFalse()) {
            return true;
        }

        return isImpatientFormula(clazz.getRepresentative());
    }

    public static boolean isImpatientFormula(Formula formula) {
        return formula.accept(INSTANCE).equals(formula.gSubformulas());
    }

    private static class AnalysisVisitor implements Visitor<Set<GOperator>> {
        private AnalysisVisitor() {

        }

        @Override
        public Set<GOperator> defaultAction(Formula formula) {
            return new HashSet<>();
        }

        @Override
        public Set<GOperator> visit(Conjunction conjunction) {
            return conjunction.union(e -> e.accept(this));
        }

        @Override
        public Set<GOperator> visit(Disjunction conjunction) {
            return conjunction.intersection(e -> e.accept(this));
        }

        @Override
        public Set<GOperator> visit(GOperator gOperator) {
            Set<GOperator> impatientGs = gOperator.operand.accept(this);
            impatientGs.add(gOperator);
            return impatientGs;
        }
    }
}
