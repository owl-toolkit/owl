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
import owl.factories.EquivalenceClassUtil;
import owl.ltl.EquivalenceClass;
import owl.util.ImmutableObject;

public final class DegeneralizedBreakpointState extends ImmutableObject {

  final EquivalenceClass current;
  // Index of the current checked obligation. A negative index means a liveness obligation is
  // checked.
  final int index;
  final EquivalenceClass[] next;
  final GObligations obligations;
  final EquivalenceClass safety;

  @Nullable
  private EquivalenceClass label = null;

  DegeneralizedBreakpointState(int index, EquivalenceClass safety, EquivalenceClass current,
    EquivalenceClass[] next, GObligations obligations) {
    assert obligations == null || ((obligations.obligations.length == 0
      && obligations.liveness.length == 0 && index == 0)
      || (-obligations.liveness.length <= index && index < obligations.obligations.length));

    // assert current.isTrue() || !safety.implies(current);

    this.index = index;
    this.current = current;
    this.obligations = obligations;
    this.safety = safety;
    this.next = next;
  }

  public static DegeneralizedBreakpointState createSink() {
    return new DegeneralizedBreakpointState(0, null, null,
      EquivalenceClassUtil.EMPTY, null);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    DegeneralizedBreakpointState that = (DegeneralizedBreakpointState) o;
    return index == that.index && Objects.equals(safety, that.safety) && Objects
      .equals(current, that.current) && Arrays.equals(next, that.next) && Objects
      .equals(obligations, that.obligations);
  }


  EquivalenceClass getLabel() {
    if (label == null) {
      label = safety.and(current);

      for (EquivalenceClass clazz : next) {
        label = label.andWith(clazz);
      }

      for (EquivalenceClass clazz : obligations.obligations) {
        label = label.andWith(clazz);
      }

      for (EquivalenceClass clazz : obligations.liveness) {
        label = label.andWith(clazz);
      }
    }

    return label;
  }

  public GObligations getObligations() {
    return obligations;
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(current, obligations, safety, index, Arrays.hashCode(next));
  }

  @Override
  public String toString() {
    return "[obligations=" + obligations
      + (safety == null || safety.isTrue() ? "" : ", safety=" + safety)
      + (index == 0 ? "" : ", index=" + index)
      + (current == null || current.isTrue() ? "" : ", current=" + current)
      + (next.length <= 0 ? "" : ", next=" + Arrays.toString(next)) + ']';
  }
}
