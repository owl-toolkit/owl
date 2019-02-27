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

package owl.collections;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public abstract class Either<A, B> {
  private Either() {}

  public abstract <C> C either(Function<? super A, ? extends C> left,
    Function<? super B, ? extends C> right);

  public static <A, B> Either<A, B> left(A value) {
    return new EitherA<>(value);
  }

  public static <A, B> Either<A, B> right(B value) {
    return new EitherB<>(value);
  }

  public Optional<A> fromLeft() {
    return this.either(Optional::of, value -> Optional.empty());
  }

  public Optional<B> fromRight() {
    return this.either(value -> Optional.empty(), Optional::of);
  }

  public boolean isLeft() {
    return fromLeft().isPresent();
  }

  public boolean isRight() {
    return fromRight().isPresent();
  }

  private static final class EitherA<A, B> extends Either<A, B> {
    private final A value;

    private EitherA(A value) {
      this.value = Objects.requireNonNull(value);
    }

    @Override
    public <C> C either(Function<? super A, ? extends C> left,
      Function<? super B, ? extends C> right) {
      return left.apply(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof EitherA)) {
        return false;
      }

      return value.equals(((EitherA<?, ?>) o).value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "A[" + value + ']';
    }
  }

  private static final class EitherB<A, B> extends Either<A, B> {
    private final B value;

    private EitherB(B value) {
      this.value = Objects.requireNonNull(value);
    }

    @Override
    public <C> C either(Function<? super A, ? extends C> left,
      Function<? super B, ? extends C> right) {
      return right.apply(value);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof EitherB)) {
        return false;
      }

      return value.equals(((EitherB<?, ?>) o).value);
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }

    @Override
    public String toString() {
      return "B[" + value + ']';
    }
  }
}
