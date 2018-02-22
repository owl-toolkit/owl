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

package owl.translations.ltl2ldba.breakpoint;

import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;
import owl.ltl.EquivalenceClass;
import owl.util.StringUtil;

public final class DegeneralizedBreakpointState {

  @Nullable
  final EquivalenceClass current;
  // Index of the current checked obligation. A negative index means a liveness obligation is
  // checked.
  final int index;
  final EquivalenceClass[] next;
  @Nullable
  final GObligations obligations;
  @Nullable
  final EquivalenceClass safety;

  @Nullable
  private EquivalenceClass label = null;
  private int hashCode = 0;

  @SuppressWarnings({"PMD.ArrayIsStoredDirectly",
                      "AssignmentToCollectionOrArrayFieldFromParameter"})
  DegeneralizedBreakpointState(int index, @Nullable EquivalenceClass safety,
    @Nullable EquivalenceClass current, EquivalenceClass[] next,
    @Nullable GObligations obligations) {
    assert obligations == null || ((obligations.obligations().size() == 0
      && obligations.liveness().size() == 0 && index == 0)
      || (-obligations.liveness().size() <= index && index < obligations.obligations().size()));

    this.index = index;
    this.current = current;
    this.obligations = obligations;
    this.safety = safety;
    this.next = next;
  }

  public static DegeneralizedBreakpointState createSink() {
    return new DegeneralizedBreakpointState(0, null, null,
      EquivalenceClass.EMPTY_ARRAY, null);
  }

  EquivalenceClass getLabel() {
    if (label == null) {
      assert safety != null && current != null && obligations != null;
      label = safety.and(current);

      for (EquivalenceClass clazz : next) {
        label = label.and(clazz);
      }

      for (EquivalenceClass clazz : obligations.obligations()) {
        label = label.and(clazz);
      }

      for (EquivalenceClass clazz : obligations.liveness()) {
        label = label.and(clazz);
      }
    }

    return label;
  }

  public GObligations getObligations() {
    assert obligations != null;
    return obligations;
  }

  @Override
  public String toString() {
    return obligations + StringUtil.join(safety == null || safety.isTrue() ? null : "GWR=" + safety,
      index == 0 ? null : "i=" + index,
      current == null || current.isTrue() ? null : "C=" + current,
      next.length <= 0 ? null : "N=" + Arrays.toString(next));
  }

  @SuppressWarnings("NonFinalFieldReferenceInEquals")
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    DegeneralizedBreakpointState other = (DegeneralizedBreakpointState) o;
    return (hashCode == 0 || other.hashCode == 0 || other.hashCode == hashCode)
      && index == other.index && Objects.equals(safety, other.safety)
      && Objects.equals(current, other.current) && Arrays.equals(next, other.next)
      && Objects.equals(obligations, other.obligations);
  }

  @Override
  public int hashCode() {
    // TODO: Hash code could potentially be 0? Not worth a boolean flag though, probably.
    if (hashCode == 0) {
      hashCode = Objects.hash(current, obligations, safety, index, Arrays.hashCode(next));
    }

    return hashCode;
  }
}
