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

import com.google.auto.value.AutoValue;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;

@AutoValue
public abstract class NullablePair<A, B> {

  @Nullable
  public abstract A fst();

  @Nullable
  public abstract B snd();

  public static <A, B> NullablePair<A, B> of(@Nullable A fst, @Nullable B snd) {
    return new AutoValue_NullablePair<>(fst, snd);
  }

  public static <A, B> Set<NullablePair<A, B>> allPairs(Set<A> fstSet, Set<B> sndSet) {
    Set<NullablePair<A, B>> pairs = new HashSet<>();

    for (A fst : fstSet) {
      for (B snd : sndSet) {
        pairs.add(NullablePair.of(fst, snd));
      }
    }

    return pairs;
  }

  public final NullablePair<B, A> swap() {
    return NullablePair.of(snd(), fst());
  }

  public final <C> NullablePair<C, B> mapFst(Function<A, C> mapper) {
    return NullablePair.of(mapper.apply(fst()), snd());
  }

  public final <C> NullablePair<A, C> mapSnd(Function<B,C> mapper) {
    return NullablePair.of(fst(), mapper.apply(snd()));
  }

  @Override
  public final String toString() {
    return String.format("(%s, %s)", fst(), snd());
  }
}
