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

package ltl;


public class PatientSlaveVisitor implements Visitor<Boolean> {

    @Override
    public Boolean visit(XOperator x) {
        return true;
    }

    @Override
    public Boolean visit(Conjunction p) {
        return p.children.stream().allMatch(child -> child.accept(this));
    }

    @Override
    public Boolean visit(Disjunction p) {
        return p.children.stream().allMatch(child -> child.accept(this));
    }

    @Override
    public Boolean defaultAction(Formula formula) {
        return false;
    }
}
