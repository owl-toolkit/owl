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

package owl.automaton;

import com.google.common.collect.ImmutableList;
import java.util.BitSet;
import java.util.List;

public final class OmegaWord {
  final ImmutableList<BitSet> loop;
  final ImmutableList<BitSet> prefix;

  private OmegaWord(List<BitSet> prefix, List<BitSet> loop) {
    assert !loop.isEmpty();
    this.prefix = ImmutableList.copyOf(prefix);
    this.loop = ImmutableList.copyOf(loop);
  }

  public static OmegaWord create(List<BitSet> loop) {
    return new OmegaWord(ImmutableList.of(), ImmutableList.copyOf(loop));
  }

  public static OmegaWord create(List<BitSet> prefix, List<BitSet> loop) {
    return new OmegaWord(ImmutableList.copyOf(prefix), ImmutableList.copyOf(loop));
  }
}
