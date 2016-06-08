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

package omega_automaton.acceptance;

import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;

import java.util.Collections;
import java.util.List;

public class NoneAcceptance implements OmegaAcceptance {

    @Override
    public String getName() {
        return "none";
    }

    @Override
    public List<Object> getNameExtra() {
        return Collections.emptyList();
    }

    @Override
    public int getAcceptanceSets() {
        return 0;
    }

    @Override
    public BooleanExpression<AtomAcceptance> getBooleanExpression() {
        return new BooleanExpression<>(false);
    }
}
