/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.ltl.tlsf;

import com.google.common.collect.BiMap;
import java.util.BitSet;
import java.util.List;
import java.util.OptionalInt;
import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.UOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultConverter;

@Value.Immutable
public abstract class Tlsf {

  @Value.Default
  public Formula assert_() {
    return BooleanConstant.TRUE;
  }

  @Value.Default
  public Formula assume() {
    return BooleanConstant.TRUE;
  }

  private Formula convert(Formula formula) {
    if (semantics().isMealy() && target().isMoore()) {
      return formula.accept(new MealyToMooreConverter());
    }

    if (semantics().isMoore() && target().isMealy()) {
      return formula.accept(new MooreToMealyConverter());
    }

    return formula;
  }

  public abstract String description();

  @Value.Default
  public Formula guarantee() {
    return BooleanConstant.TRUE;
  }

  @Value.Default
  public Formula initially() {
    return BooleanConstant.TRUE;
  }

  // Main / Body
  public abstract BitSet inputs();

  public abstract BiMap<String, Integer> mapping();

  public abstract BitSet outputs();

  @Value.Default
  public Formula preset() {
    return BooleanConstant.TRUE;
  }

  @Value.Default
  public Formula require() {
    return BooleanConstant.TRUE;
  }

  public abstract Semantics semantics();

  public abstract Semantics target();

  // Info Header
  public abstract String title();

  @Value.Derived
  public LabelledFormula toFormula() {
    Formula formula = Disjunction.of(FOperator.of(require().not()), assume().not(),
      Conjunction.of(GOperator.of(assert_()), guarantee()));

    if (semantics().isStrict()) {
      formula = Conjunction.of(preset(), formula, Disjunction
        .of(UOperator.of(assert_(), require().not()), GOperator.of(assert_())));
    } else {
      formula = Conjunction.of(preset(), formula);
    }

    Formula result = convert(Disjunction.of(initially().not(), formula));
    BiMap<Integer, String> mapping = mapping().inverse();
    OptionalInt maximalIndex = mapping.keySet().stream().mapToInt(Integer::intValue).max();
    if (!maximalIndex.isPresent()) {
      return LabelledFormula.of(result, List.of());
    }
    String[] variables = new String[maximalIndex.getAsInt() + 1];
    mapping.forEach((index, name) -> variables[index] = name);
    for (int i = 0; i < variables.length; i++) {
      if (variables[i] == null) {
        variables[i] = "p" + i;
      }
    }
    return LabelledFormula.of(result, List.of(variables));
  }

  public enum Semantics {
    MEALY, MEALY_STRICT, MOORE, MOORE_STRICT;

    public boolean isMealy() {
      switch (this) {
        case MEALY:
        case MEALY_STRICT:
          return true;

        default:
          return false;
      }
    }

    public boolean isMoore() {
      return !isMealy();
    }

    public boolean isStrict() {
      switch (this) {
        case MEALY_STRICT:
        case MOORE_STRICT:
          return true;

        default:
          return false;
      }
    }
  }

  private final class MealyToMooreConverter extends DefaultConverter {
    @Override
    public Formula visit(Literal literal) {
      if (outputs().get(literal.getAtom())) {
        return new XOperator(literal);
      }

      return literal;
    }
  }

  private final class MooreToMealyConverter extends DefaultConverter {
    @Override
    public Formula visit(Literal literal) {
      if (inputs().get(literal.getAtom())) {
        return new XOperator(literal);
      }

      return literal;
    }
  }
}
