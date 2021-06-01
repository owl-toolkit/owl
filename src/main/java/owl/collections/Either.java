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

package owl.collections;

import com.google.auto.value.AutoOneOf;
import java.util.function.Function;

@AutoOneOf(Either.Type.class)
public abstract class Either<A, B> {
  Either() {
    // Constructor should only be visible to package.
  }

  public enum Type {
    LEFT, RIGHT
  }

  public abstract Type type();

  public abstract A left();

  public abstract B right();

  public static <A, B> Either<A, B> left(A value) {
    return AutoOneOf_Either.left(value);
  }

  public static <A, B> Either<A, B> right(B value) {
    return AutoOneOf_Either.right(value);
  }

  public final <C> C map(Function<? super A, ? extends C> left,
    Function<? super B, ? extends C> right) {
    switch (type()) {
      case LEFT:
        return left.apply(left());

      case RIGHT:
        return right.apply(right());

      default:
        throw new AssertionError("Unreachable.");
    }
  }

  @Override
  public final String toString() {
    return map(a -> "A[" + a + ']', b -> "B[" + b + ']');
  }
}
