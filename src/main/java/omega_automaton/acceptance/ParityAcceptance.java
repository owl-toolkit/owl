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
import java.util.Objects;

public class ParityAcceptance implements OmegaAcceptance {

    public enum Priority {
        EVEN {
            public Priority not() {
                return ODD;
            }

            @Override
            public String toString() {
                return "even";
            }
        },

        ODD {
            public Priority not() {
                return EVEN;
            }

            @Override
            public String toString() {
                return "odd";
            }
        };

        public abstract Priority not();
    }

    @Nonnegative
    private final int colors;
    private final Priority priority;

    public ParityAcceptance(@Nonnegative int colors) {
        this(colors, Priority.ODD);
    }

    public ParityAcceptance(@Nonnegative int colors, Priority priority) {
        this.colors = colors;
        this.priority = priority;
    }

    public ParityAcceptance complement() {
        return new ParityAcceptance(colors, priority.not());
    }

    @Override
    public String getName() {
        return "parity";
    }

    @Override
    public List<Object> getNameExtra() {
        return Arrays.asList("min", priority.toString(), colors);
    }

    @Override
    public int getAcceptanceSets() {
        return colors;
    }

    @Override
    public BooleanExpression<AtomAcceptance> getBooleanExpression() {
        if (colors == 0) {
            return new BooleanExpression<>(priority == Priority.EVEN);
        }

        int i = colors - 1;

        BooleanExpression<AtomAcceptance> exp = mkColor(i);

        for (i--; 0 <= i; i--) {
            if (i % 2 == 0 ^ priority == Priority.EVEN) {
                exp = mkColor(i).and(exp);
            } else {
                exp = mkColor(i).or(exp);
            }
        }

        return exp;
    }

    private BooleanExpression<AtomAcceptance> mkColor(int i) {
        return (i % 2 == 0 ^ priority == Priority.EVEN) ? HOAConsumerExtended.mkFin(i) : HOAConsumerExtended.mkInf(i);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ParityAcceptance that = (ParityAcceptance) o;
        return colors == that.colors &&
                priority == that.priority;
    }

    @Override
    public int hashCode() {
        return Objects.hash(colors, priority);
    }
}
