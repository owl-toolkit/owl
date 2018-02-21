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
import owl.ltl.EquivalenceClass;
import owl.util.StringUtil;

public final class GeneralizedBreakpointState {

  final EquivalenceClass[] current;
  final EquivalenceClass[] next;
  final GObligations obligations;
  final EquivalenceClass safety;
  private int hashCode = 0;

  @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "AssignmentOrReturnOfFieldWithMutableType"})
  private GeneralizedBreakpointState(GObligations obligations, EquivalenceClass safety,
    EquivalenceClass[] current, EquivalenceClass[] next) {
    this.obligations = obligations;
    this.safety = safety;
    this.current = current;
    this.next = next;
  }

  static GeneralizedBreakpointState of(GObligations obligations, EquivalenceClass safety,
    EquivalenceClass[] current, EquivalenceClass[] next) {
    return new GeneralizedBreakpointState(obligations, safety, current, next);
  }

  public GObligations getObligations() {
    return obligations;
  }

  @Override
  public String toString() {
    return obligations + StringUtil.join(safety.isTrue() ? null : "GWR=" + safety,
      current.length <= 0 ? null : "C=" + Arrays.toString(current),
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

    GeneralizedBreakpointState that = (GeneralizedBreakpointState) o;
    return (hashCode == 0 || that.hashCode == 0 || that.hashCode == hashCode)
      && Objects.equals(safety, that.safety) && Objects.equals(obligations, that.obligations)
      && Arrays.equals(current, that.current) && Arrays.equals(next, that.next);
  }

  @Override
  public int hashCode() {
    // TODO: Hash code could potentially be 0? Not worth a boolean flag though, probably.
    if (hashCode == 0) {
      hashCode = Objects.hash(safety, obligations, Arrays.hashCode(current), Arrays.hashCode(next));
    }

    return hashCode;
  }
}
