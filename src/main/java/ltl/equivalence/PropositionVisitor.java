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
import ltl.visitors.VoidVisitor;

import java.util.HashMap;
import java.util.Map;

/**
 * For the propositional view on LTL modal operators (F, G, U, X) and
 * literals (a, !a) are treated as propositions.
 *
 * @return
 */
class PropositionVisitor implements VoidVisitor {

    private final Map<Formula, Integer> mapping;

    private PropositionVisitor() {
        mapping = new HashMap<>();
    }

    static Map<Formula, Integer> extractPropositions(Formula formula) {
        PropositionVisitor visitor = new PropositionVisitor();
        formula.accept(visitor);
        return visitor.mapping;
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
        mapping.put(fOperator, -1);
        fOperator.operand.accept(this);
    }

    @Override
    public void visit(GOperator gOperator) {
        mapping.put(gOperator, -1);
        gOperator.operand.accept(this);
    }

    @Override
    public void visit(Literal literal) {
        mapping.put(literal, -1);
    }

    @Override
    public void visit(UOperator uOperator) {
        mapping.put(uOperator, -1);
        uOperator.left.accept(this);
        uOperator.right.accept(this);
    }

    @Override
    public void visit(XOperator xOperator) {
        mapping.put(xOperator, -1);
        xOperator.operand.accept(this);
    }
}
