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

package owl.util;

import com.google.common.collect.ImmutableList;
import java.util.function.UnaryOperator;

public final class UnaryOperators {

  private UnaryOperators(){
  }

  public static <T> UnaryOperator<T> chain(ImmutableList<UnaryOperator<T>> operators) {
    return (x) -> chain(x, operators);
  }

  private static <T> T chain(T input, ImmutableList<UnaryOperator<T>> operators) {
    T output = input;

    for (UnaryOperator<T> operator : operators) {
      output = operator.apply(output);
    }

    return output;
  }
}
