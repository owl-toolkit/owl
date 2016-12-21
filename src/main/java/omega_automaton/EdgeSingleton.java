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
import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;

public class EdgeSingleton<S> implements Edge<S> {

    private final S successor;
    private final int acceptance;

    public EdgeSingleton(S successor) {
        this(successor, -1);
    }

    public EdgeSingleton(S successor, int acceptance) {
        this.successor = successor;
        this.acceptance = acceptance >= 0 ? acceptance : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null) {
            return false;
        }

        if (o instanceof EdgeSingleton) {
            EdgeSingleton<?> that = (EdgeSingleton<?>) o;
            return acceptance == that.acceptance && Objects.equals(successor, that.successor);
        }

        if (o instanceof Edge) {
            Edge<?> that = (Edge<?>) o;
            return Objects.equals(successor, that.getSuccessor()) && that.inSet(acceptance) && Iterators.elementsEqual(iterator(), that.iterator());
        }

        return false;
    }

    @Override
    public S getSuccessor() {
        return successor;
    }

    @Override
    public int hashCode() {
        return 31 * (31 + successor.hashCode()) + acceptance;
    }

    @Override
    public boolean inSet(@Nonnegative int i) {
        return i == acceptance;
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return (acceptance >= 0) ? IntStream.of(acceptance).iterator() : IntStream.empty().iterator();
    }

    @Override
    public String toString() {
        return "-> " + successor + " {" + acceptance + '}';
    }
}
