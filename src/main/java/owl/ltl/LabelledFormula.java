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

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import java.util.List;
import owl.collections.Collections3;
import owl.ltl.visitors.PrintVisitor;

@AutoValue
public abstract class LabelledFormula {
  public abstract Formula formula();

  public abstract List<String> variables();

  public static LabelledFormula of(Formula formula, List<String> variables) {
    var copiedVariables = List.copyOf(variables);
    checkState(Collections3.isDistinct(copiedVariables));
    return new AutoValue_LabelledFormula(formula, copiedVariables);
  }

  public LabelledFormula wrap(Formula formula) {
    return of(formula, variables());
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
