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

import java.util.BitSet;
import java.util.Objects;

public class Edge<S> {

    public final S successor;
    public final BitSet acceptance;

    public Edge(S successor, BitSet acceptance) {
        this.successor = successor;
        this.acceptance = acceptance;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Edge<?> tuple = (Edge<?>) o;
        return Objects.equals(successor, tuple.successor) &&
                Objects.equals(acceptance, tuple.acceptance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(successor, acceptance);
    }

    @Override
    public String toString() {
        return "-> " + successor + " {" + acceptance + '}';
    }
}
