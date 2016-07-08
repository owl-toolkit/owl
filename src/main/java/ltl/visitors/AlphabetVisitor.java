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

public class AlphabetVisitor implements VoidVisitor {

    private int alphabetSize;

    private AlphabetVisitor() {
        alphabetSize = 0;
    }

    public static int extractAlphabet(Formula formula) {
        AlphabetVisitor visitor = new AlphabetVisitor();
        formula.accept(visitor);
        return visitor.alphabetSize;
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
        fOperator.operand.accept(this);
    }

    @Override
    public void visit(GOperator gOperator) {
        gOperator.operand.accept(this);
    }

    @Override
    public void visit(Literal literal) {
        alphabetSize = Math.max(literal.getAtom() + 1, alphabetSize);
    }

    @Override
    public void visit(UOperator uOperator) {
        uOperator.left.accept(this);
        uOperator.right.accept(this);
    }

    @Override
    public void visit(ROperator rOperator) {
        rOperator.left.accept(this);
        rOperator.right.accept(this);
    }

    @Override
    public void visit(XOperator xOperator) {
        xOperator.operand.accept(this);
    }
}
