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


import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import ltl.Formula;

/**
 * EquivalenceClass interface.
 * The general contract of this interface is: If two implementing objects were
 * created from different factories, implies and equals have to return
 * {@code false}.
 */
public interface EquivalenceClass {

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

  EquivalenceClass and(EquivalenceClass eq);

  /**
   * Performs the same operation as {@link EquivalenceClass#and}, but also calls free() on the
   * instance {@link this}
   */
  EquivalenceClass andWith(EquivalenceClass eq);

  EquivalenceClass duplicate();

  EquivalenceClass exists(Predicate<Formula> predicate);

  void free();

  void freeRepresentative();

  /**
   * Collects all literals used in the bdd and stores the corresponding atoms in the BitSet.
   */
  BitSet getAtoms();

  @Nullable
  Formula getRepresentative();

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
   */
  <T extends Formula> Set<T> getSupport(Class<T> clazz);

  default Set<Formula> getSupport(Predicate<Formula> predicate) {
    return getSupport().stream().filter(predicate).collect(Collectors.toSet());
  }

  boolean implies(EquivalenceClass equivalenceClass);

  boolean isFalse();

  boolean isTrue();

  EquivalenceClass or(EquivalenceClass eq);

  /**
   * Performs the same operation as {@link EquivalenceClass#or}, but also calls free() on the
   * instance {@link this}
   */
  EquivalenceClass orWith(EquivalenceClass eq);

  default ImmutableList<Set<Formula>> satisfyingAssignments() {
    return satisfyingAssignments(getSupport());
  }

  ImmutableList<Set<Formula>> satisfyingAssignments(Iterable<? extends Formula> support);

  EquivalenceClass substitute(Function<? super Formula, ? extends Formula> substitution);

  EquivalenceClass temporalStep(BitSet valuation);

  EquivalenceClass temporalStepUnfold(BitSet valuation);

  boolean testSupport(Predicate<Formula> predicate);

  EquivalenceClass unfold();

  EquivalenceClass unfoldTemporalStep(BitSet valuation);
}
