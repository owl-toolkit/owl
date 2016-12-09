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

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EquivalenceClass interface.
 * <p>
 * The general contract of this interface is: If two implementing objects were
 * created from different factories, implies and equals have to return
 * {@code false}.
 */
public interface EquivalenceClass {

    @Nullable
    Formula getRepresentative();

    boolean implies(EquivalenceClass equivalenceClass);

    EquivalenceClass unfold();

    EquivalenceClass apply(Function<? super Formula, ? extends Formula> function);

    EquivalenceClass temporalStep(BitSet valuation);

    EquivalenceClass temporalStepUnfold(BitSet valuation);

    EquivalenceClass unfoldTemporalStep(BitSet valuation);

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

    EquivalenceClass exists(Predicate<Formula> predicate);

    void free();

    boolean testSupport(Predicate<Formula> predicate);

    /**
     * Compute the support of the EquivalenceClass.
     *
     * @return All literals and modal operators this equivalence class depends on.
     */
    default Set<Formula> getSupport() {
        return getSupport(Formula.class);
    }

    /**
     * Compute the support of the EquivalenceClass and restrict the set to a particular type.
     *
     * @param clazz
     * @param <T>
     * @return
     */
    <T extends Formula> Set<T> getSupport(Class<T> clazz);

    default Set<Formula> getSupport(Predicate<Formula> predicate) {
        return getSupport().stream().filter(predicate).collect(Collectors.toSet());
    }

    default Collection<Set<Formula>> satisfyingAssignments() {
        return restrictedSatisfyingAssignments(getSupport(), null);
    }

    Collection<Set<Formula>> restrictedSatisfyingAssignments(Collection<Formula> support, EquivalenceClass restr);

    /**
     * Collects all literals used in the bdd and stores the corresponding atoms in the BitSet.
     * @return
     */
    BitSet getAtoms();

    void freeRepresentative();

    EquivalenceClass substitute(Function<Formula, Formula> substitution);

    static void free(@Nullable EquivalenceClass clazz) {
        if (clazz != null) {
            clazz.free();
        }
    }

    static void free(@Nullable EquivalenceClass[] classes) {
        if (classes == null) {
            return;
        }

        for (EquivalenceClass clazz : classes) {
            free(clazz);
        }
    }

    static void free(@Nullable EquivalenceClass clazz, @Nullable EquivalenceClass... classes) {
        free(clazz);
        free(classes);
    }

    static void free(@Nullable Iterable<EquivalenceClass> classes) {
        if (classes == null) {
            return;
        }

        for (EquivalenceClass clazz : classes) {
            free(clazz);
        }
    }
}
