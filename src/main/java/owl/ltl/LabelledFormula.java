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

package owl.ltl;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.auto.value.AutoValue;
import java.util.List;
import owl.collections.Collections3;
import owl.ltl.visitors.PrintVisitor;

@AutoValue
public abstract class LabelledFormula {
  public abstract List<String> atomicPropositions();

  public abstract Formula formula();

  public static LabelledFormula of(Formula formula, List<String> atomicProposition) {
    int atomicPropositionsSize = formula.atomicPropositions(true).length();
    checkArgument(Collections3.isDistinct(atomicProposition));
    checkArgument(atomicPropositionsSize <= atomicProposition.size());
    return new AutoValue_LabelledFormula(
      List.copyOf(atomicProposition.subList(0, atomicPropositionsSize)), formula);
  }

  public LabelledFormula wrap(Formula formula) {
    return of(formula, atomicPropositions());
  }

  public LabelledFormula not() {
    return wrap(formula().not());
  }

  public LabelledFormula nnf() {
    return wrap(formula().nnf());
  }

  @Override
  public String toString() {
    return PrintVisitor.toString(this, false);
  }
}
