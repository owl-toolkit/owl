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

package ltl.equivalence;


import ltl.*;

public class EvaluateVisitor implements Visitor<Formula> {

    private final EquivalenceClassFactory factory;
    private final EquivalenceClass environment;
    private final EquivalenceClass falseEnvironment;

    public EvaluateVisitor(EquivalenceClassFactory factory, Conjunction environment) {
        this.factory = factory;
        this.environment = factory.createEquivalenceClass(environment);
        this.falseEnvironment = factory.createEquivalenceClass(environment.not());
    }

    @Override
    public Formula visit(BooleanConstant c) {
        return c;
    }

    @Override
    public Formula defaultAction(Formula f) {
        EquivalenceClass clazz = factory.createEquivalenceClass(f);

        if (environment.implies(clazz)) {
            return BooleanConstant.TRUE;
        }

        if (clazz.implies(falseEnvironment)) {
            return BooleanConstant.FALSE;
        }

        return f;
    }

    @Override
    public Formula visit(Conjunction c) {
        return Conjunction.create(c.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction d) {
        Formula defaultAction = defaultAction(d);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return Disjunction.create(d.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
        Formula defaultAction = defaultAction(fOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return (new FOperator(fOperator.operand.accept(this)));
    }

    @Override
    public Formula visit(GOperator gOperator) {
        Formula defaultAction = defaultAction(gOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return (new GOperator(gOperator.operand.accept(this)));
    }

    @Override
    public Formula visit(UOperator uOperator) {
        Formula defaultAction = defaultAction(uOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return (new UOperator(uOperator.left.accept(this), uOperator.right.accept(this)));
    }

    @Override
    public Formula visit(XOperator xOperator) {
        Formula defaultAction = defaultAction(xOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return (new XOperator(xOperator.operand.accept(this)));
    }
}
