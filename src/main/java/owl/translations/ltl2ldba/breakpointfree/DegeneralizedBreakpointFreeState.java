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

import java.util.Objects;
import javax.annotation.Nonnegative;
import owl.ltl.EquivalenceClass;
import owl.util.ImmutableObject;

public final class DegeneralizedBreakpointFreeState extends ImmutableObject {

  @Nonnegative // Index of the current checked liveness (F) obligation.
  final int index;
  final EquivalenceClass liveness;
  final FGObligations obligations;
  final EquivalenceClass safety;

  DegeneralizedBreakpointFreeState(@Nonnegative int index, EquivalenceClass safety,
    EquivalenceClass liveness, FGObligations obligations) {
    assert 0 == index || (0 < index && index < obligations.liveness.length);

    this.index = index;
    this.liveness = liveness;
    this.obligations = obligations;
    this.safety = safety;
  }

  public static DegeneralizedBreakpointFreeState createSink() {
    return new DegeneralizedBreakpointFreeState(0, null, null, null);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    DegeneralizedBreakpointFreeState that = (DegeneralizedBreakpointFreeState) o;
    return index == that.index && Objects.equals(safety, that.safety) && Objects
      .equals(liveness, that.liveness) && Objects.equals(obligations, that.obligations);
  }

  public FGObligations getObligations() {
    return obligations;
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(liveness, obligations, safety, index);
  }

  @Override
  public String toString() {
    return "[obligations=" + obligations
      + (safety.isTrue() ? "" : ", safety=" + safety)
      + (index == 0 ? "" : ", index=" + index)
      + (liveness.isTrue() ? "" : ", current-liveness=" + liveness)
      + ']';
  }
}
