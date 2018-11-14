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

import java.util.Objects;
import javax.annotation.Nullable;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.GOperator;
import owl.ltl.LtlLanguageExpressible;
import owl.translations.canonical.RoundRobinState;
import owl.util.StringUtil;

public final class AcceptingComponentState implements LtlLanguageExpressible {

  @Nullable
  final EquivalenceClass safety;
  @Nullable
  final RoundRobinState<EquivalenceClass> liveness;
  @Nullable
  final FGObligations obligations;

  private final int hashCode;

  AcceptingComponentState(
    @Nullable EquivalenceClass safety,
    @Nullable RoundRobinState<EquivalenceClass> liveness,
    @Nullable FGObligations obligations) {
    this.liveness = liveness;
    this.obligations = obligations;
    this.safety = safety;
    this.hashCode = Objects.hash(liveness, obligations, safety);
  }

  public static AcceptingComponentState createSink() {
    return new AcceptingComponentState(null, null, null);
  }

  @Nullable
  public FGObligations getObligations() {
    return obligations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof AcceptingComponentState)) {
      return false;
    }

    AcceptingComponentState that = (AcceptingComponentState) o;
    return that.hashCode == hashCode
      && Objects.equals(safety, that.safety)
      && Objects.equals(liveness, that.liveness)
      && Objects.equals(obligations, that.obligations);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public EquivalenceClass language() {
    var factory = safety.factory();
    var liveness = Conjunction.of(obligations.rewrittenOperators.stream()
      .filter(FOperator.class::isInstance).map(GOperator::new));
    return safety.and(factory.of(liveness));
  }

  @Override
  public String toString() {
    return obligations + StringUtil.join(
      safety == null || safety.isTrue() ? null : "GWR=" + safety,
      liveness == null ? null : "FUM=" + liveness);
  }
}
