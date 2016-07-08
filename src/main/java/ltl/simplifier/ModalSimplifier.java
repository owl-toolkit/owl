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

import ltl.*;
import ltl.visitors.Visitor;

import java.util.ArrayList;
import java.util.List;

/* Pushes down F,G in the syntax tree
* TODO: Reuse objects! */
class ModalSimplifier implements Visitor<Formula> {

    @Override
    public Formula visit(FOperator fOperator) {
        Formula operand = fOperator.operand.accept(this);

        if (operand.isPureEventual() || operand.isSuspendable()) {
            return operand;
        }

        return FOperator.create(operand);
    }

    @Override
    public Formula visit(GOperator gOperator) {
        Formula operand = gOperator.operand.accept(this);

        if (operand.isPureUniversal() || operand.isSuspendable()) {
            return operand;
        }

        return GOperator.create(operand);
    }

    @Override
    public Formula visit(UOperator uOperator) {
        Formula left = uOperator.left.accept(this);
        Formula right = uOperator.right.accept(this);

        if (right.isSuspendable() || right.isPureEventual()) {
            return right;
        }

        if (left.isSuspendable() || left.isPureUniversal()) {
            return Disjunction.create(Conjunction.create(left, FOperator.create(right)), right);
        }

        if (left.isPureEventual()) {
            return Disjunction.create(new FOperator(Conjunction.create(left, new XOperator(right))), right);
        }

        return UOperator.create(left, right);
    }

    @Override
    public Formula visit(ROperator rOperator) {
        Formula left = rOperator.left.accept(this);
        Formula right = rOperator.right.accept(this);

        if (right.isSuspendable() || right.isPureUniversal()) {
            return right;
        }

        return ROperator.create(left, right);
    }

    @Override
    public Formula visit(XOperator xOperator) {
        Formula operand = xOperator.operand.accept(this);

        if (operand.isSuspendable()) {
            return operand;
        }

        // Only call constructor, when necessary.
        if (operand == xOperator.operand) {
            return xOperator;
        }

        return new XOperator(operand);
    }

    @Override
    public Formula visit(Conjunction conjunction) {
        boolean newElement = false;
        List<Formula> newChildren = new ArrayList<>(conjunction.children.size());

        for (Formula child : conjunction.children) {
            Formula newChild = child.accept(this);

            if (newChild == BooleanConstant.FALSE) {
                return BooleanConstant.FALSE;
            }

            newElement |= (child != newChild);
            newChildren.add(newChild);
        }

        // Only call constructor, when necessary.
        Formula c = newElement ? Conjunction.create(newChildren.stream()) : conjunction;

        if (c instanceof Conjunction) {
            Conjunction c2 = (Conjunction) c;

            if (c2.children.stream().anyMatch(e -> c2.children.contains(e.not()))) {
                return BooleanConstant.FALSE;
            }
        }

        return c;
    }

    @Override
    public Formula visit(Disjunction disjunction) {
        boolean newElement = false;
        List<Formula> newChildren = new ArrayList<>(disjunction.children.size());

        for (Formula child : disjunction.children) {
            Formula newChild = child.accept(this);

            if (newChild == BooleanConstant.TRUE) {
                return BooleanConstant.TRUE;
            }

            newElement |= (child != newChild);
            newChildren.add(newChild);
        }

        // Only call constructor, when necessary.
        Formula d = newElement ? Disjunction.create(newChildren.stream()) : disjunction;

        if (d instanceof Disjunction) {
            Disjunction d2 = (Disjunction) d;

            if (d2.children.stream().anyMatch(e -> d2.children.contains(e.not()))) {
                return BooleanConstant.TRUE;
            }
        }

        return d;
    }

    @Override
    public Formula defaultAction(Formula formula) {
        return formula;
    }
}
