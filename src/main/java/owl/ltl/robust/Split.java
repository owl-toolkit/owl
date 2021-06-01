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
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;

@AutoValue
public abstract class Split {

  public static final Split TRUE = of(BooleanConstant.TRUE, true);

  public static final Split FALSE = of(BooleanConstant.FALSE, true);


  public abstract Formula always();

  public abstract Formula eventuallyAlways();

  public abstract Formula infinitelyOften();

  public abstract Formula eventually();

  abstract boolean grFree();

  static Split of(Formula formula, boolean grFree) {
    return new AutoValue_Split(formula, formula, formula, formula, grFree);
  }

  static Split of(Formula first, Formula second, Formula third, Formula fourth,
    boolean grFree) {
    return new AutoValue_Split(first, second, third, fourth, grFree);
  }

  public static BinaryOperator<Split> combiner(BinaryOperator<Formula> formulaCombiner) {
    return (one, other) -> of(formulaCombiner.apply(one.always(), other.always()),
      formulaCombiner.apply(one.eventuallyAlways(), other.eventuallyAlways()),
      formulaCombiner.apply(one.infinitelyOften(), other.infinitelyOften()),
      formulaCombiner.apply(one.eventually(), other.eventually()),
      one.grFree() && other.grFree());
  }


  public Split map(UnaryOperator<Formula> map) {
    return map(map, grFree());
  }

  public Split map(UnaryOperator<Formula> map, boolean grFree) {
    return of(map.apply(always()), map.apply(eventuallyAlways()), map.apply(infinitelyOften()),
      map.apply(eventually()), grFree);
  }


  public List<Formula> all() {
    return List.of(always(), eventuallyAlways(), infinitelyOften(), eventually());
  }

  public Formula get(Robustness level) {
    switch (level) {
      case ALWAYS:
        return always();
      case EVENTUALLY_ALWAYS:
        return eventuallyAlways();
      case INFINITELY_OFTEN:
        return infinitelyOften();
      case EVENTUALLY:
        return eventually();
      case NEVER:
        return eventually().not();
      default:
        throw new AssertionError();
    }
  }

  @Override
  public String toString() {
    return "G: " + always() + " FG: " + eventuallyAlways() + " GF: " + infinitelyOften()
      + " F: " + eventually();
  }
}
