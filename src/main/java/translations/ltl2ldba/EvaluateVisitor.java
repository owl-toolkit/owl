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

import java.util.HashSet;
import java.util.Set;

class EvaluateVisitor implements Visitor<Formula> {

    private final Set<GOperator> gMonitors;
    private final Set<Formula> universalTruths;

    private final EquivalenceClassFactory factory;
    private final EquivalenceClass environment;

    public EvaluateVisitor(Set<GOperator> environment) {
        this(null, environment);
    }

    public EvaluateVisitor(EquivalenceClassFactory factory, Set<GOperator> gMonitors) {
        this.gMonitors = gMonitors;
        universalTruths = new HashSet<>(gMonitors.size());
        gMonitors.forEach(gOperator -> universalTruths.add(gOperator.operand));

        if (factory != null) {
            this.factory = factory;

            EquivalenceClass environment = factory.getTrue();

            for (Formula formula : Sets.union(gMonitors, universalTruths)) {
                EquivalenceClass formulaClazz = factory.createEquivalenceClass(formula);
                environment = environment.andWith(formulaClazz);
                formulaClazz.free();
            }

            this.environment = environment;
        } else {
            this.factory = null;
            this.environment = null;
        }
    }

    @Override
    public Formula visit(BooleanConstant c) {
        return c;
    }

    @Override
    public Formula defaultAction(Formula f) {
        if (universalTruths.contains(f)) {
            return BooleanConstant.TRUE;
        }

        if (factory != null) {
            EquivalenceClass clazz = factory.createEquivalenceClass(f);

            if (environment.implies(clazz)) {
                return BooleanConstant.TRUE;
            }
        }

        return f;
    }

    @Override
    public Formula visit(Conjunction c) {
        Formula defaultAction = defaultAction(c);

        if (defaultAction instanceof BooleanConstant) {
            return defaultAction;
        }

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

        return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
        return BooleanConstant.get(gMonitors.contains(gOperator));
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
