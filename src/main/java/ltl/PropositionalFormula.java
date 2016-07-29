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

import com.google.common.collect.ImmutableSet;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class PropositionalFormula extends ImmutableObject implements Formula {

    public final Set<Formula> children;

    protected PropositionalFormula(Iterable<? extends Formula> children) {
        this.children = ImmutableSet.copyOf(children);
    }

    protected PropositionalFormula(Formula... children) {
        this.children = ImmutableSet.copyOf(children);
    }

    protected PropositionalFormula(Stream<? extends Formula> formulaStream) {
        children = ImmutableSet.copyOf(formulaStream.iterator());
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(3 * children.size());

        s.append('(');

        Iterator<Formula> iter = children.iterator();

        while (iter.hasNext()) {
            s.append(iter.next());

            if (iter.hasNext()) {
                s.append(getOperator());
            }
        }

        s.append(')');

        return s.toString();
    }

    @Override
    public boolean equals2(ImmutableObject o) {
        PropositionalFormula that = (PropositionalFormula) o;
        return Objects.equals(children, that.children);
    }

    @Override
    public boolean isPureEventual() {
        return allMatch(Formula::isPureEventual);
    }

    @Override
    public boolean isPureUniversal() {
        return allMatch(Formula::isPureUniversal);
    }

    @Override
    public boolean isSuspendable() {
        return allMatch(Formula::isSuspendable);
    }

    public <E> Set<E> union(Function<Formula, Collection<E>> f) {
        Set<E> set = new HashSet<>(children.size());
        children.forEach(c -> set.addAll(f.apply(c)));
        return set;
    }

    public <E> Set<E> intersection(Function<Formula, Collection<E>> f) {
        Set<E> set = new HashSet<>(children.size());

        if (children.isEmpty()) {
            return set;
        }

        Iterator<Formula> iterator = children.iterator();

        set.addAll(f.apply(iterator.next()));
        iterator.forEachRemaining(c -> set.retainAll(f.apply(c)));
        return set;
    }

    public boolean allMatch(Predicate<Formula> p) {
        return children.stream().allMatch(p);
    }

    public boolean anyMatch(Predicate<Formula> p) {
        return children.stream().anyMatch(p);
    }

    protected abstract char getOperator();
}
