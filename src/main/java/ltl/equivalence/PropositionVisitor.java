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
import ltl.visitors.IntVisitor;

import java.util.*;

/**
 * For the propositional view on LTL modal operators (F, G, U, X) and
 * literals (a, !a) are treated as propositions.
 *
 * TODO: Extract alphabet.
 *
 * @return
 */
class PropositionVisitor implements IntVisitor {

    private final Deque<Formula> mapping;

    private PropositionVisitor() {
        mapping = new ArrayDeque<>();
    }

    static Deque<Formula> extractPropositions(Formula formula) {
        PropositionVisitor visitor = new PropositionVisitor();
        formula.accept(visitor);
        return visitor.mapping;
    }

    @Override
    public int visit(BooleanConstant formula) {
        return 0;
    }

    @Override
    public int visit(Conjunction conjunction) {
        return visit((PropositionalFormula) conjunction);
    }

    @Override
    public int visit(Disjunction disjunction) {
        return visit((PropositionalFormula) disjunction);
    }

    @Override
    public int visit(FOperator fOperator) {
        return visit((UnaryModalOperator) fOperator);
    }

    @Override
    public int visit(FrequencyG freq) {
        return visit((UnaryModalOperator) freq);
    }

    @Override
    public int visit(GOperator gOperator) {
        return visit((UnaryModalOperator) gOperator);
    }

    @Override
    public int visit(Literal literal) {
        mapping.add(literal);
        return 0;
    }

    @Override
    public int visit(MOperator mOperator) {
        return visit((BinaryModalOperator) mOperator);
    }

    @Override
    public int visit(ROperator rOperator) {
        return visit((BinaryModalOperator) rOperator);
    }

    @Override
    public int visit(UOperator uOperator) {
        return visit((BinaryModalOperator) uOperator);
    }

    @Override
    public int visit(WOperator wOperator) {
        return visit((BinaryModalOperator) wOperator);
    }

    @Override
    public int visit(XOperator xOperator) {
        return visit((UnaryModalOperator) xOperator);
    }

    private int visit(BinaryModalOperator operator) {
        operator.left.accept(this);
        operator.right.accept(this);
        mapping.add(operator);
        return 0;
    }

    private int visit(PropositionalFormula formula) {
        formula.children.forEach(c -> c.accept(this));
        return 0;
    }

    private int visit(UnaryModalOperator operator) {
        operator.operand.accept(this);
        mapping.add(operator);
        return 0;
    }
}
