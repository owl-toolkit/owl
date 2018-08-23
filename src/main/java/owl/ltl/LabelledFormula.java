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

package owl.ltl;

import static com.google.common.base.Preconditions.checkState;

import de.tum.in.naturals.bitset.BitSets;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;
import owl.collections.Collections3;
import owl.ltl.visitors.PrintVisitor;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class LabelledFormula {
  public abstract Formula formula();

  public abstract List<String> variables();

  public abstract Set<String> player1Variables();

  @Value.Check
  void check() {
    checkState(Collections3.isDistinct(variables()));
    checkState(variables().containsAll(player1Variables()));
  }


  public static LabelledFormula of(Formula formula, List<String> variables) {
    return LabelledFormulaTuple.create(formula, variables, variables);
  }

  public static LabelledFormula of(Formula formula, List<String> variables, Set<String> player1) {
    return LabelledFormulaTuple.create(formula, variables, player1);
  }

  public static LabelledFormula of(Formula formula, List<String> variables, BitSet player1) {
    Set<String> player1Variables = new HashSet<>();
    BitSets.forEach(player1, i -> player1Variables.add(variables.get(i)));
    return LabelledFormulaTuple.create(formula, variables, Set.copyOf(player1Variables));
  }


  public LabelledFormula wrap(Formula formula) {
    return of(formula, variables(), player1Variables());
  }

  public LabelledFormula split(Set<String> player1) {
    return of(formula(), variables(), player1);
  }

  public LabelledFormula not() {
    return wrap(formula().not());
  }

  @Override
  public String toString() {
    return PrintVisitor.toString(this, false);
  }
}
