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
import owl.util.ImmutableObject;

public final class GeneralizedBreakpointFreeState extends ImmutableObject {

  final EquivalenceClass[] liveness;
  final FGObligations obligations;
  final EquivalenceClass safety;

  GeneralizedBreakpointFreeState(EquivalenceClass safety, EquivalenceClass[] liveness,
    FGObligations obligations) {
    this.liveness = liveness;
    this.obligations = obligations;
    this.safety = safety;
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    GeneralizedBreakpointFreeState that = (GeneralizedBreakpointFreeState) o;
    return Objects.equals(obligations, that.obligations) && Objects.equals(safety, that.safety)
      && Arrays.equals(liveness, that.liveness);
  }

  public FGObligations getObligations() {
    return obligations;
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(Arrays.hashCode(liveness), obligations, safety);
  }

  @Override
  public String toString() {
    return "[obligations=" + obligations
      + (safety.isTrue() ? "" : ", safety=" + safety)
      + ", liveness=" + Arrays.toString(liveness)
      + ']';
  }
}
