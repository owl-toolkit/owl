/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.ltl2nba;

import java.util.Objects;
import javax.annotation.Nullable;
import owl.ltl.Formula;
import owl.translations.canonical.RoundRobinState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;
import owl.util.StringUtil;

public final class ProductState {

  public final Formula safety;
  @Nullable
  public final RoundRobinState<Formula> liveness;

  @Nullable
  public final SymmetricEvaluatedFixpoints evaluatedFixpoints;
  @Nullable
  public final SymmetricEvaluatedFixpoints.NonDeterministicAutomata automata;

  private final int hashCode;

  ProductState(
    Formula safety,
    @Nullable RoundRobinState<Formula> liveness,
    @Nullable SymmetricEvaluatedFixpoints evaluatedFixpoints,
    @Nullable SymmetricEvaluatedFixpoints.NonDeterministicAutomata automata) {
    this.liveness = liveness;
    this.evaluatedFixpoints = evaluatedFixpoints;
    this.safety = safety;
    this.automata = automata;
    this.hashCode = Objects.hash(liveness, evaluatedFixpoints, safety);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ProductState)) {
      return false;
    }

    ProductState that = (ProductState) o;
    return that.hashCode == hashCode
      && Objects.equals(safety, that.safety)
      && Objects.equals(liveness, that.liveness)
      && Objects.equals(evaluatedFixpoints, that.evaluatedFixpoints);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    return evaluatedFixpoints
      + StringUtil.join("GWR=" + safety, liveness == null ? null : "FUM=" + liveness);
  }
}
