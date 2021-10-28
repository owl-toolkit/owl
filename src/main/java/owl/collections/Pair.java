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
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@AutoValue
public abstract class Pair<A, B> {

  public abstract A fst();

  public abstract B snd();

  public static <A, B> Pair<A, B> of(A fst, B snd) {
    Objects.requireNonNull(fst);
    Objects.requireNonNull(snd);
    return new AutoValue_Pair<>(fst, snd);
  }

  public static <A, B> Set<Pair<A, B>> allPairs(Set<? extends A> fstSet, Set<? extends B> sndSet) {
    Set<Pair<A, B>> pairs = new HashSet<>();

    for (A fst : fstSet) {
      for (B snd : sndSet) {
        pairs.add(Pair.of(fst, snd));
      }
    }

    return pairs;
  }

  public Pair<B, A> swap() {
    return Pair.of(snd(), fst());
  }

  public <C> Pair<C, B> mapFst(Function<? super A, ? extends C> mapper) {
    return Pair.of(mapper.apply(fst()), snd());
  }

  public <C> Pair<A, C> mapSnd(Function<? super B, ? extends C> mapper) {
    return Pair.of(fst(), mapper.apply(snd()));
  }

  @Override
  public final String toString() {
    return String.format("(%s, %s)", fst(), snd());
  }
}
