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

package ltl.equivalence;


import ltl.Formula;

import java.util.BitSet;
import java.util.List;
import java.util.function.Function;

/**
 * EquivalenceClass interface.
 * <p>
 * The general contract of this interface is: If two implementing objects were
 * created from different factories, implies and equivalent have to return
 * {@code false}.
 */
public interface EquivalenceClass {

    Formula getRepresentative();

    boolean implies(EquivalenceClass equivalenceClass);

    /**
     * Check if two classes are equivalent. Implementing classes are expected to
     * implement equivalent and equals, such that they agree on their return
     * values.
     *
     * @param equivalenceClass
     * @return
     */
    boolean equivalent(EquivalenceClass equivalenceClass);

    EquivalenceClass unfold();

    EquivalenceClass apply(Function<? super Formula, ? extends Formula> function);

    EquivalenceClass temporalStep(BitSet valuation);

    EquivalenceClass and(EquivalenceClass eq);

    /**
     * Performs the same operation as {@link EquivalenceClass#and}, but also calls free() on the instance {@link this}
     * @param eq
     * @return
     */
    EquivalenceClass andWith(EquivalenceClass eq);

    EquivalenceClass or(EquivalenceClass eq);

    /**
     * Performs the same operation as {@link EquivalenceClass#or}, but also calls free() on the instance {@link this}
     * @param eq
     * @return
     */
    EquivalenceClass orWith(EquivalenceClass eq);

    boolean isTrue();

    boolean isFalse();

    void free();

    List<Formula> getSupport();
}
