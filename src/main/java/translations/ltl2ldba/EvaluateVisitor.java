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

import com.google.common.collect.Sets;
import ltl.*;
import ltl.equivalence.EquivalenceClass;
import ltl.equivalence.EquivalenceClassFactory;
import ltl.visitors.Visitor;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class EvaluateVisitor implements Visitor<Formula> {

    @Nullable
    private final EquivalenceClass environment;
    @Nullable
    private final EquivalenceClassFactory factory;
    private final Set<Formula> universalTruths;

    EvaluateVisitor(Set<GOperator> gMonitors) {
        this(gMonitors, null);
    }

    EvaluateVisitor(Set<GOperator> gMonitors, @Nullable EquivalenceClassFactory equivalenceClassFactory) {
        universalTruths = new HashSet<>(gMonitors.size());
        gMonitors.forEach(gOperator -> universalTruths.add(gOperator.operand));

        if (equivalenceClassFactory != null) {
            factory = equivalenceClassFactory;
            environment = equivalenceClassFactory.createEquivalenceClass(Sets.union(gMonitors, universalTruths));
        } else {
            factory = null;
            environment = null;
        }
    }

    void free() {
        if (environment != null) {
            environment.free();
        }
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
        return booleanConstant;
    }

    @Override
    public Formula defaultAction(Formula formula) {
        if (universalTruths.contains(formula)) {
            return BooleanConstant.TRUE;
        }

        if (factory != null && environment != null) {
            EquivalenceClass clazz = factory.createEquivalenceClass(formula);

            if (environment.implies(clazz)) {
                clazz.free();
                return BooleanConstant.TRUE;
            }

            clazz.free();
        }

        return formula;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
        Formula defaultAction = defaultAction(conjunction);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return Conjunction.create(conjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
        Formula defaultAction = defaultAction(disjunction);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return Disjunction.create(disjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
        Formula defaultAction = defaultAction(fOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
        return BooleanConstant.get(universalTruths.contains(gOperator.operand));
    }

    @Override
    public Formula visit(UOperator uOperator) {
        Formula defaultAction = defaultAction(uOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
        Formula defaultAction = defaultAction(rOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return ROperator.create(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(XOperator xOperator) {
        Formula defaultAction = defaultAction(xOperator);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

        return XOperator.create(xOperator.operand.accept(this));
    }
}
