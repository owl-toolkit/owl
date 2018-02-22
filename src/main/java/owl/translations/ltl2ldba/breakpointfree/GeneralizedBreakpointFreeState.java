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

package owl.translations.ltl2ldba.breakpointfree;

import java.util.Arrays;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.util.StringUtil;

public final class GeneralizedBreakpointFreeState {

  final EquivalenceClass[] liveness;
  final FGObligations obligations;
  final EquivalenceClass safety;
  private int hashCode = 0;

  @SuppressWarnings({"PMD.ArrayIsStoredDirectly", "AssignmentOrReturnOfFieldWithMutableType"})
  GeneralizedBreakpointFreeState(EquivalenceClass safety, EquivalenceClass[] liveness,
    FGObligations obligations) {
    this.liveness = liveness;
    this.obligations = obligations;
    this.safety = safety;
  }

  public FGObligations getObligations() {
    return obligations;
  }

  @Override
  public String toString() {
    return obligations + StringUtil.join(
      safety.isTrue() ? null : "GWR=" + safety,
      liveness.length <= 0 ? null : "FUM=" + Arrays.toString(liveness));
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

    GeneralizedBreakpointFreeState other = (GeneralizedBreakpointFreeState) o;
    return (hashCode == 0 || other.hashCode == 0 || other.hashCode == hashCode)
      && Objects.equals(obligations, other.obligations) && Objects.equals(safety, other.safety)
      && Arrays.equals(liveness, other.liveness);
  }

  @Override
  public int hashCode() {
    // TODO: Hash code could potentially be 0? Not worth a boolean flag though, probably.
    if (hashCode == 0) {
      hashCode = Objects.hash(Arrays.hashCode(liveness), obligations, safety);
    }

    return hashCode;
  }
}
