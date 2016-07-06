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
import omega_automaton.output.HOAConsumerExtended;

import javax.annotation.Nonnegative;
import java.util.Arrays;
import java.util.List;

public class ParityAcceptance implements OmegaAcceptance {

    @Nonnegative
    private final int colors;

    public ParityAcceptance(@Nonnegative int colors) {
        this.colors = colors;
    }

    @Override
    public String getName() {
        return "parity";
    }

    @Override
    public List<Object> getNameExtra() {
        return Arrays.asList("min", "odd", colors + 1);
    }

    @Override
    public int getAcceptanceSets() {
        return colors + 1;
    }

    @Override
    public BooleanExpression<AtomAcceptance> getBooleanExpression() {
        if (colors == 0) {
            return new BooleanExpression<>(false);
        }

        int i = colors;

        BooleanExpression<AtomAcceptance> exp = mkColor(i);

        for (i--; 0 <= i; i--) {
            if (i % 2 == 0) {
                exp = mkColor(i).and(exp);
            } else {
                exp = mkColor(i).or(exp);
            }
        }

        return exp;
    }

    private static BooleanExpression<AtomAcceptance> mkColor(int i) {
        return (i % 2 == 0) ? HOAConsumerExtended.mkFin(i) : HOAConsumerExtended.mkInf(i);
    }
}
