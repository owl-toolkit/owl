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

package owl.ltl.robust;

import com.google.auto.value.AutoValue;
import java.util.EnumSet;
import java.util.List;
import java.util.function.UnaryOperator;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;

@AutoValue
public abstract class LabelledSplit {
  abstract Split split();

  public abstract List<String> variables();

  public LabelledFormula always() {
    return LabelledFormula.of(split().always(), variables());
  }

  public LabelledFormula eventuallyAlways() {
    return LabelledFormula.of(split().eventuallyAlways(), variables());
  }

  public LabelledFormula infinitelyOften() {
    return LabelledFormula.of(split().infinitelyOften(), variables());
  }

  public LabelledFormula eventually() {
    return LabelledFormula.of(split().eventually(), variables());
  }

  public static LabelledSplit of(Split split, List<String> variables) {
    return new AutoValue_LabelledSplit(split, List.copyOf(variables));
  }

  public LabelledFormula toLtl(EnumSet<Robustness> robustness) {
    return LabelledFormula.of(Robustness.buildFormula(split(), robustness), variables());
  }

  public LabelledSplit map(UnaryOperator<Formula> map) {
    return of(split().map(map), variables());
  }

  @Override
  public String toString() {
    return "G: " + always() + " | "
      + "FG: " + eventuallyAlways() + " | "
      + "GF: " + infinitelyOften() + " | "
      + "F: " + eventually();
  }
}
