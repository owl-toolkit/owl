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

package omega_automaton;

import com.google.common.collect.Iterators;

import javax.annotation.Nonnegative;
import java.util.BitSet;
import java.util.Objects;
import java.util.stream.IntStream;

public class EdgeGeneric<S> implements Edge<S> {

    private final S successor;
    private final BitSet acceptance;

    public EdgeGeneric(S successor, BitSet acceptance) {
        this.successor = successor;
        this.acceptance = acceptance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (o instanceof EdgeGeneric) {
            EdgeGeneric<?> that = (EdgeGeneric<?>) o;
            return Objects.equals(acceptance, that.acceptance) && Objects.equals(successor, that.successor);
        }

        if (o instanceof Edge) {
            Edge<?> that = (Edge<?>) o;
            return Objects.equals(successor, that.getSuccessor()) && Iterators.elementsEqual(stream().iterator(), that.stream().iterator());
        }

        return false;
    }

    @Override
    public S getSuccessor() {
        return successor;
    }

    @Override
    public int hashCode() {
        // Compute a to EdgeSingleton compatible hashcode.
        return 31 * (31 + successor.hashCode())
                + (acceptance.cardinality() > 1 ? acceptance.hashCode() : acceptance.nextSetBit(0));
    }

    @Override
    public boolean inSet(@Nonnegative int i) {
        return acceptance.get(i);
    }

    @Override
    public IntStream stream() {
        return acceptance.stream();
    }

    @Override
    public String toString() {
        return "-> " + successor + " {" + acceptance + '}';
    }
}
