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

package owl.translations.ltl2ldba;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LtlLanguageExpressible;
import owl.ltl.SyntacticFragments;
import owl.translations.canonical.RoundRobinState;
import owl.translations.mastertheorem.SymmetricEvaluatedFixpoints;

public final class SymmetricProductState implements LtlLanguageExpressible {

  public final EquivalenceClass safety;
  @Nullable
  public final RoundRobinState<EquivalenceClass> liveness;

  public final SymmetricEvaluatedFixpoints evaluatedFixpoints;
  public final SymmetricEvaluatedFixpoints.DeterministicAutomata automata;

  private final int hashCode;

  SymmetricProductState(
    EquivalenceClass safety,
    @Nullable RoundRobinState<EquivalenceClass> liveness,
    SymmetricEvaluatedFixpoints evaluatedFixpoints,
    SymmetricEvaluatedFixpoints.DeterministicAutomata automata) {
    this.liveness = liveness;
    this.evaluatedFixpoints = evaluatedFixpoints;
    this.safety = safety;
    this.automata = automata;
    this.hashCode = Objects.hash(liveness, evaluatedFixpoints, safety);
    assert SyntacticFragments.isSafety(safety);
    assert liveness == null || SyntacticFragments.isCoSafety(liveness.state());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof SymmetricProductState)) {
      return false;
    }

    SymmetricProductState that = (SymmetricProductState) o;
    return hashCode == that.hashCode
      && safety.equals(that.safety)
      && Objects.equals(liveness, that.liveness)
      && evaluatedFixpoints.equals(that.evaluatedFixpoints);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public EquivalenceClass language() {
    var factory = safety.factory();
    return safety.and(factory.of(Conjunction.of(evaluatedFixpoints.infinitelyOften))).unfold();
  }

  public boolean isCoveredBy(SymmetricProductState that) {
    if (!this.safety.implies(that.safety)) {
      return false;
    }

    EquivalenceClass thisLiveness = liveness == null
      ? safety.factory().of(true)
      : liveness.state();

    EquivalenceClass thatLiveness = that.liveness == null
      ? safety.factory().of(true)
      : that.liveness.state();

    if (!thisLiveness.implies(thatLiveness)) {
      return false;
    }

    if (!evaluatedFixpoints.almostAlways.containsAll(that.evaluatedFixpoints.almostAlways)) {
      return false;
    }

    return evaluatedFixpoints.infinitelyOften.containsAll(that.evaluatedFixpoints.infinitelyOften);
  }

  @Override
  public String toString() {
    String[] pieces = {
      safety.isTrue() ? null : "GWR=" + safety,
      liveness == null ? null : "FUM=" + liveness
    };

    return evaluatedFixpoints + Arrays.stream(pieces)
      .filter(Objects::nonNull)
      .collect(Collectors.joining(", ", " [", "]"));
  }
}
