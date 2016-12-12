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

public class RestrictToFGXU extends DefaultConverter {

    @Override
    public Formula visit(ROperator rOperator) {
        Formula left = rOperator.left.accept(this);
        Formula right = rOperator.right.accept(this);

        return Disjunction.create(GOperator.create(right), UOperator.create(right, Conjunction.create(left, right)));
    }

    @Override
    public Formula visit(WOperator wOperator) {
        Formula left = wOperator.left.accept(this);
        Formula right = wOperator.right.accept(this);

        return Disjunction.create(GOperator.create(left), UOperator.create(left, right));
    }

    @Override
    public Formula visit(MOperator mOperator) {
        Formula left = mOperator.left.accept(this);
        Formula right = mOperator.right.accept(this);

        return UOperator.create(left, Conjunction.create(left, right));
    }
}
