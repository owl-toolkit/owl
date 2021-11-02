/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.ltl.Formula;
import owl.translations.canonical.RoundRobinState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class ProductState {

  public final Formula safety;
  @Nullable
  public final RoundRobinState<Formula> liveness;

  public final SymmetricEvaluatedFixpoints evaluatedFixpoints;
  public final SymmetricEvaluatedFixpoints.NonDeterministicAutomata automata;

  private final int hashCode;

  ProductState(
    Formula safety,
    @Nullable RoundRobinState<Formula> liveness,
    SymmetricEvaluatedFixpoints evaluatedFixpoints,
    SymmetricEvaluatedFixpoints.NonDeterministicAutomata automata) {
    this.liveness = liveness;
    this.evaluatedFixpoints = Objects.requireNonNull(evaluatedFixpoints);
    this.safety = Objects.requireNonNull(safety);
    this.automata = Objects.requireNonNull(automata);
    this.hashCode = Objects.hash(liveness, evaluatedFixpoints, safety);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof ProductState that)) {
      return false;
    }

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
    String[] pieces = {
      "GWR=" + safety,
      liveness == null ? null : "FUM=" + liveness
    };

    return evaluatedFixpoints + Arrays.stream(pieces)
      .filter(Objects::nonNull)
      .collect(Collectors.joining(", ", " [", "]"));
  }
}
