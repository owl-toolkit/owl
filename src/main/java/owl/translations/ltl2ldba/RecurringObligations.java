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

package owl.translations.ltl2ldba;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import owl.ltl.EquivalenceClass;
import owl.ltl.GOperator;
import owl.util.ImmutableObject;

public class RecurringObligations extends ImmutableObject {

  private static final EquivalenceClass[] EMPTY = new EquivalenceClass[0];
  final Set<GOperator> associatedGs;
  // G(liveness[]) is a liveness language.
  final EquivalenceClass[] liveness;

  // obligations[] are co-safety languages.
  final EquivalenceClass[] obligations;
  // G(safety) is a safety language.
  @Nullable
  final EquivalenceClass safety;

  RecurringObligations(EquivalenceClass safety) {
    this(safety, Arrays.asList(EMPTY), Arrays.asList(EMPTY));
  }

  RecurringObligations(@Nullable EquivalenceClass safety, List<EquivalenceClass> liveness,
    List<EquivalenceClass> obligations) {

    if (safety != null) {
      // safety.freeRepresentative();
    }

    liveness.forEach(EquivalenceClass::freeRepresentative);
    obligations.forEach(EquivalenceClass::freeRepresentative);

    this.safety = safety;
    this.obligations = obligations.toArray(EMPTY);
    this.liveness = liveness.toArray(EMPTY);
    this.associatedGs = new HashSet<>();
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    RecurringObligations that = (RecurringObligations) o;
    return safety.equals(that.safety) && Arrays.equals(liveness, that.liveness) && Arrays
      .equals(obligations, that.obligations);
  }

  void forEach(Consumer<EquivalenceClass> consumer) {
    consumer.accept(safety);

    for (EquivalenceClass clazz : liveness) {
      consumer.accept(clazz);
    }

    for (EquivalenceClass clazz : obligations) {
      consumer.accept(clazz);
    }
  }

  EquivalenceClass getObligation() {
    EquivalenceClass obligation = safety.duplicate();

    for (EquivalenceClass clazz : liveness) {
      obligation = obligation.andWith(clazz);
    }

    for (EquivalenceClass clazz : obligations) {
      obligation = obligation.andWith(clazz);
    }

    return obligation;
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(safety, Arrays.hashCode(liveness), Arrays.hashCode(obligations));
  }

  boolean implies(RecurringObligations other) {
    // TODO: fix memory leak.
    return getObligation().implies(other.getObligation());
  }

  public boolean isEmpty() {
    return safety.isTrue() && obligations.length == 0 && liveness.length == 0;
  }

  public boolean isPureLiveness() {
    return obligations.length == 0 && safety.isTrue();
  }

  public boolean isPureSafety() {
    return obligations.length == 0 && liveness.length == 0;
  }

  @Override
  public String toString() {
    StringBuilder stringBuilder = new StringBuilder(50);

    stringBuilder.append('<');
    if (safety != null && !safety.isTrue()) {
      stringBuilder.append("safety=").append(safety);
    }

    if (liveness.length > 0) {
      if (safety != null && !safety.isTrue()) {
        stringBuilder.append(", ");
      }

      stringBuilder.append("liveness=").append(Arrays.toString(liveness));
    }

    if (obligations.length > 0) {
      if (safety != null && !safety.isTrue() || liveness.length > 0) {
        stringBuilder.append(", ");
      }

      stringBuilder.append("obligations=").append(Arrays.toString(obligations));
    }

    stringBuilder.append('>');
    return stringBuilder.toString();
  }
}
