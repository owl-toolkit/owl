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

package ltl.visitors;

import ltl.*;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class Collector implements VoidVisitor {

    private final Predicate<Formula> collect;
    private final Set<Formula> collection;

    public Collector(Predicate<Formula> predicate) {
        collect = predicate;
        collection = new HashSet<>();
    }

    public Set<Formula> getCollection() {
        return Collections.unmodifiableSet(collection);
    }

    @Override
    public void visit(Conjunction conjunction) {
        conjunction.children.forEach(c -> c.accept(this));
    }

    @Override
    public void visit(Disjunction disjunction) {
        disjunction.children.forEach(c -> c.accept(this));
    }

    @Override
    public void visit(FOperator fOperator) {
        if (collect.test(fOperator)) {
            collection.add(fOperator);
        }

        fOperator.operand.accept(this);
    }

    @Override
    public void visit(GOperator gOperator) {
        if (collect.test(gOperator)) {
            collection.add(gOperator);
        }

        gOperator.operand.accept(this);
    }

    @Override
    public void visit(Literal literal) {
        if (collect.test(literal)) {
            collection.add(literal);
        }
    }

    @Override
    public void visit(ROperator rOperator) {
        if (collect.test(rOperator)) {
            collection.add(rOperator);
        }

        rOperator.left.accept(this);
        rOperator.right.accept(this);
    }

    @Override
    public void visit(UOperator uOperator) {
        if (collect.test(uOperator)) {
            collection.add(uOperator);
        }

        uOperator.left.accept(this);
        uOperator.right.accept(this);
    }

    @Override
    public void visit(XOperator xOperator) {
        if (collect.test(xOperator)) {
            collection.add(xOperator);
        }

        xOperator.operand.accept(this);
    }

}
