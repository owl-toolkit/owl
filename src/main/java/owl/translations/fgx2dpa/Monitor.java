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

package owl.translations.fgx2dpa;

import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;
import org.immutables.value.Value;
import owl.ltl.Formula;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class Monitor<F extends UnaryModalOperator> {
  abstract F formula();

  abstract Set<Formula> currentTokens();

  @Value.Derived
  @Value.Auxiliary
  Set<Formula> finalStates() {
    return Sets.filter(currentTokens(), t ->
      t.accept(SafetyAutomaton.FinalStateVisitor.INSTANCE));
  }

  @Value.Derived
  @Value.Auxiliary
  Set<Formula> nonFinalStates() {
    return Sets.difference(currentTokens(), finalStates());
  }


  public static <F extends UnaryModalOperator> Monitor<F>
  of(F formula, Set<Formula> currentTokens) {
    return MonitorTuple.create(formula, currentTokens);
  }

  public static <F extends UnaryModalOperator> Monitor<F> of(F formula) {
    return of(formula, Set.of(formula.operand));
  }

  public Monitor<F> temporalStep(BitSet valuation) {
    Set<Formula> currentTokens = new HashSet<>(Set.of(formula().operand));
    nonFinalStates().forEach(t -> currentTokens.add(t.temporalStep(valuation)));
    return of(formula(), currentTokens);
  }
}
