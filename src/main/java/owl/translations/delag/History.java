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

package owl.translations.delag;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.primitives.ImmutableLongArray;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import owl.ltl.Literal;

final class History {

  private static final Map<ImmutableLongArray, ImmutableLongArray> uniqueHistory = new HashMap<>();
  private final ImmutableLongArray longs;

  History() {
    this.longs = makeUnique(new long[]{});
  }

  History(long[] longs) {
    this.longs = makeUnique(longs);
  }

  static History create(long[] requiredHistory) {
    return new History(requiredHistory);
  }

  private static ImmutableLongArray makeUnique(long[] history) {
    ImmutableLongArray array = ImmutableLongArray.copyOf(history);
    ImmutableLongArray uniqueElement = uniqueHistory.get(array);

    if (uniqueElement != null) {
      return uniqueElement;
    }

    uniqueHistory.put(array, array);
    return array;
  }

  static History stepHistory(@Nullable History past, BitSet present, History mask) {
    checkArgument(present.length() < 64);

    long[] pastLongs = new long[mask.longs.length()];
    long[] presentLongs = present.toLongArray();

    if (past != null && past.longs.length() > 1 && pastLongs.length == past.longs.length()) {
      System.arraycopy(past.longs.toArray(), 1, pastLongs, 0, past.longs.length() - 1);
    }

    if (pastLongs.length > 0 && presentLongs.length > 0) {
      pastLongs[pastLongs.length - 1] = presentLongs[0];
    }

    for (int i = 0; i < pastLongs.length; i++) {
      pastLongs[i] &= mask.longs.get(i);
    }

    return new History(pastLongs);
  }

  @Override
  public boolean equals(Object o) {
    return this == o
      || (o != null && getClass() == o.getClass() && longs.equals(((History) o).longs));
  }

  boolean get(int time, Literal literal) {
    checkArgument(literal.getAtom() < 64);
    return (((longs.get(time) >> literal.getAtom()) & 1L) == 1L) ^ literal.isNegated();
  }

  @Override
  public int hashCode() {
    return longs.hashCode();
  }

  int size() {
    return longs.length();
  }

  @Override
  public String toString() {
    return "H=" + longs.asList().stream().map(x -> BitSet.valueOf(new long[] {x}).toString())
      .toList();
  }
}
