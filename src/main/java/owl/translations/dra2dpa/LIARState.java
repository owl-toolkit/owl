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

package owl.translations.dra2dpa;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.Arrays;
import owl.automaton.AnnotatedState;

@AutoValue
public abstract class LIARState<R> implements AnnotatedState<R> {
  @Override
  public abstract R state();

  public abstract int[] record();

  public abstract int e();

  public abstract int f();


  public static <R> LIARState<R> of(R originalState, int[] record, int e, int f) {
    return new AutoValue_LIARState<>(originalState, record, e, f);
  }


  @Memoized
  @Override
  @SuppressWarnings("PMD.OverrideBothEqualsAndHashcode")
  public abstract int hashCode();

  @Override
  public String toString() {
    return String.format("{%s,%s,%s,%s}", state(), Arrays.toString(record()), e(), f());
  }
}
