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

package omega_automaton.output;

import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import ltl.*;

public class FormulaConverter implements ltl.Visitor<BooleanExpression<AtomLabel>> {

    private static final BooleanExpression<AtomLabel> TRUE = new BooleanExpression<>(true);
    private static final BooleanExpression<AtomLabel> FALSE = new BooleanExpression<>(false);

    private static BooleanExpression<AtomLabel> getConstant(boolean b) {
        return b ? TRUE : FALSE;
    }

    @Override
    public BooleanExpression<AtomLabel> defaultAction(Formula f) {
        throw new IllegalArgumentException("Cannot convert " + f + " to BooleanExpression.");
    }

    @Override
    public BooleanExpression<AtomLabel> visit(BooleanConstant b) {
        return getConstant(b.value);
    }

    @Override
    public BooleanExpression<AtomLabel> visit(Conjunction c) {
        if (c.children.isEmpty()) {
            return TRUE;
        }

        return c.children.stream().map(e -> e.accept(this)).reduce(BooleanExpression::and).get();
    }

    @Override
    public BooleanExpression<AtomLabel> visit(Disjunction d) {
        if (d.children.isEmpty()) {
            return FALSE;
        }

        return d.children.stream().map(e -> e.accept(this)).reduce(BooleanExpression::or).get();
    }

    @Override
    public BooleanExpression<AtomLabel> visit(Literal l) {
        BooleanExpression<AtomLabel> atom = new BooleanExpression<>(AtomLabel.createAPIndex(l.getAtom()));

        if (l.isNegated()) {
            atom = atom.not();
        }

        return atom;
    }
}
