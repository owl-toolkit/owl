/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
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

import java.util.List;
import java.util.Objects;
import owl.ltl.EquivalenceClass;
import owl.util.StringUtil;

public final class GeneralizedBreakpointFreeState {

  final EquivalenceClass safety;
  final List<EquivalenceClass> liveness;
  final FGObligations obligations;
  private final int hashCode;

  GeneralizedBreakpointFreeState(EquivalenceClass safety, List<EquivalenceClass> liveness,
    FGObligations obligations) {
    this.safety = safety;
    this.liveness = List.copyOf(liveness);
    this.obligations = obligations;
    this.hashCode = Objects.hash(this.liveness, this.obligations, this.safety);
  }

  public FGObligations getObligations() {
    return obligations;
  }

  @Override
  public String toString() {
    return obligations + StringUtil.join(
      safety.isTrue() ? null : "GWR=" + safety,
      liveness.isEmpty() ? null : "FUM=" + liveness);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || !getClass().equals(o.getClass())) {
      return false;
    }

    GeneralizedBreakpointFreeState other = (GeneralizedBreakpointFreeState) o;
    return (hashCode == other.hashCode)
      && Objects.equals(obligations, other.obligations)
      && Objects.equals(safety, other.safety)
      && liveness.equals(other.liveness);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }
}
