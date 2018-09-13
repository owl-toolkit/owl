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

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class PromisedSet {

  abstract Set<GOperator> formulaeG();

  abstract List<FOperator> formulaeF();

  abstract Formula firstF();


  @Value.Auxiliary
  @Value.Derived
  long nonFinalGCount() {
    return formulaeG().stream()
      .map(x -> x.operand)
      .filter(operand -> !operand.accept(SafetyAutomaton.FinalStateVisitor.INSTANCE))
      .count();
  }

  @Value.Auxiliary
  @Value.Derived
  Set<UnaryModalOperator> union() {
    return Sets.union(formulaeG(), Set.copyOf(formulaeF()));
  }


  public static PromisedSet of(Set<GOperator> formulaeG, List<FOperator> formulaeF,
                               Formula firstF) {
    return PromisedSetTuple.create(formulaeG, formulaeF, firstF);
  }

  public static PromisedSet of(Set<GOperator> formulaeG, List<FOperator> formulaeF) {
    return of(formulaeG, formulaeF, Iterables.getFirst(formulaeF, BooleanConstant.TRUE));
  }
}
