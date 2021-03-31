/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

@AutoOneOf(Result.Type.class)
public abstract class Result<S, F> {

  Result() {
    // Constructor should only be visible to package.
  }

  public enum Type {
    SUCCESS, FAILURE
  }

  public abstract Type type();

  public abstract S success();

  public abstract F failure();

  public static <S, F> Result<S, F> success(S value) {
    return AutoOneOf_Result.success(value);
  }

  public static <S, F> Result<S, F> failure(F value) {
    return AutoOneOf_Result.failure(value);
  }

  public final <C> C map(
    Function<? super S, ? extends C> successFunction,
    Function<? super F, ? extends C> failureFunction) {

    switch (type()) {
      case SUCCESS:
        return successFunction.apply(success());

      case FAILURE:
        return failureFunction.apply(failure());

      default:
        throw new AssertionError("Unreachable.");
    }
  }

  public final <NewF> Result<S, NewF> propagateSuccess() {
    return success(success());
  }

  public final <NewS> Result<NewS, F> propagateFailure() {
    return failure(failure());
  }

  @Override
  public final String toString() {
    return map(a -> "Success: " + a, b -> "Failure: " + b);
  }
}
