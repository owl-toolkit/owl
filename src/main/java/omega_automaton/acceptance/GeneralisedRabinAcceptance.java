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
import omega_automaton.acceptance.OmegaAcceptance;
import omega_automaton.collections.TranSet;
import omega_automaton.output.HOAConsumerExtended;

import java.util.ArrayList;
import java.util.List;

public class GeneralisedRabinAcceptance implements OmegaAcceptance {

    int sets;
    int[] pairs;

    @Override
    public String getName() {
        return "generalized-Rabin";
    }

    @Override
    public List<Object> getNameExtra() {
        List<Object> extra = new ArrayList<>(pairs.length + 1);
        extra.add(pairs.length);

        for (int pair : pairs) {
            extra.add(pair);
        }

        return extra;
    }

    @Override
    public int getAcceptanceSets() {
        return sets;
    }

    @Override
    public BooleanExpression<AtomAcceptance> getBooleanExpression() {
        int i = 0;
        BooleanExpression<AtomAcceptance> disjunction = null;

        for (int pair : pairs) {
            BooleanExpression<AtomAcceptance> conjunction = HOAConsumerExtended.mkFin(i);
            i++;

            for (int j = pair; j > 0; j--) {
                conjunction.and(HOAConsumerExtended.mkInf(i));
                i++;
            }

            if (disjunction == null) {
                disjunction = conjunction;
            } else {
                disjunction = disjunction.or(conjunction);
            }
        }

        return disjunction;
    }
}
