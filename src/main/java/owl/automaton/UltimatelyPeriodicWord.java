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

package owl.automaton;

import static java.util.stream.Collectors.toUnmodifiableList;

import com.google.common.base.Preconditions;
import de.tum.in.naturals.bitset.ImmutableBitSet;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;

public final class UltimatelyPeriodicWord {

  public final List<BitSet> prefix;
  public final List<BitSet> period;

  public UltimatelyPeriodicWord(List<BitSet> prefix, List<BitSet> period) {
    this.prefix = prefix.stream().map(ImmutableBitSet::copyOf).collect(toUnmodifiableList());
    this.period = period.stream().map(ImmutableBitSet::copyOf).collect(toUnmodifiableList());
    Preconditions.checkArgument(!this.period.isEmpty());
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (!(o instanceof UltimatelyPeriodicWord)) {
      return false;
    }

    UltimatelyPeriodicWord that = (UltimatelyPeriodicWord) o;
    return prefix.equals(that.prefix) && period.equals(that.period);
  }

  @Override
  public int hashCode() {
    return Objects.hash(prefix, period);
  }
}
