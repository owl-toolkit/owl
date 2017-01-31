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

package translations.ltl2ldba.ng;

import ltl.*;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.simplifier.Simplifier;
import ltl.visitors.DefaultVisitor;
import translations.ltl2ldba.Evaluator;

import java.util.Set;

class RecurringObligations2Evaluator implements Evaluator<RecurringObligations2> {

    private final EquivalenceClassFactory factory;

    RecurringObligations2Evaluator(EquivalenceClassFactory factory) {
        this.factory = factory;
    }

    @Override
    public EquivalenceClass evaluate(EquivalenceClass clazz, RecurringObligations2 obligation) {
         Formula formula = clazz.getRepresentative();
        SubstitutionVisitor substitutionVisitor = new SubstitutionVisitor(obligation);
        Formula subst = formula.accept(substitutionVisitor);
        Formula evaluated = Simplifier.simplify(subst, Simplifier.Strategy.MODAL);
        return factory.createEquivalenceClass(evaluated);
    }

    static class SubstitutionVisitor extends DefaultVisitor<Formula> {

        private Set<FOperator> trueFs;
        private Set<GOperator> trueGs;

        SubstitutionVisitor(Set<FOperator> fOperators, Set<GOperator> gOperators) {
            trueFs = fOperators;
            trueGs = gOperators;
        }

        SubstitutionVisitor(RecurringObligations2 obligations2) {
            trueFs = obligations2.associatedFs;
            trueGs = obligations2.associatedGs;
        }

        @Override
        protected Formula defaultAction(Formula formula) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Formula visit(BooleanConstant booleanConstant) {
            return booleanConstant;
        }

        @Override
        public Formula visit(Conjunction conjunction) {
            return Conjunction.create(conjunction.children.stream().map(e -> e.accept(this)));
        }

        @Override
        public Formula visit(Disjunction disjunction) {
            return Disjunction.create(disjunction.children.stream().map(e -> e.accept(this)));
        }

        @Override
        public Formula visit(FOperator fOperator) {
            return BooleanConstant.get(trueFs.contains(fOperator));
        }

        @Override
        public Formula visit(GOperator gOperator) {
            return BooleanConstant.get(trueGs.contains(gOperator));
        }

        @Override
        public Formula visit(Literal literal) {
            return literal;
        }

        @Override
        public Formula visit(UOperator uOperator) {
            if (!trueFs.contains(new FOperator(uOperator.right))) {
                return BooleanConstant.FALSE;
            }

            return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
        }

        @Override
        public Formula visit(XOperator xOperator) {
            return XOperator.create(xOperator.operand.accept(this));
        }
    }
}