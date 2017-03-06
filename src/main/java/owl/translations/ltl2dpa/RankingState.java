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

package owl.translations.ltl2dpa;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import owl.collections.Trie;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.DegeneralizedBreakpointState;
import owl.util.ImmutableObject;

@Immutable
public final class RankingState extends ImmutableObject {

  final ImmutableList<DegeneralizedBreakpointState> ranking;
  final EquivalenceClass state;
  final int volatileIndex;

  private RankingState(EquivalenceClass state, ImmutableList<DegeneralizedBreakpointState> ranking,
    int volatileIndex) {

    this.volatileIndex = volatileIndex;
    this.state = state;
    this.ranking = ranking;
  }

  static RankingState createSink() {
    return create(null);
  }

  static RankingState create(EquivalenceClass initialComponentState) {
    return create(initialComponentState, ImmutableList.of(), 0, null);
  }

  static RankingState create(EquivalenceClass initialComponentState,
    List<DegeneralizedBreakpointState> ranking, int volatileIndex,
    @Nullable Map<EquivalenceClass, Trie<DegeneralizedBreakpointState>> trieMap) {
    if (trieMap != null) {
      trieMap.computeIfAbsent(initialComponentState, x -> new Trie<>()).add(ranking);
    }

    return new RankingState(initialComponentState, ImmutableList.copyOf(ranking), volatileIndex);
  }

  @Override
  protected boolean equals2(ImmutableObject o) {
    RankingState that = (RankingState) o;
    return that.volatileIndex == this.volatileIndex && Objects.equals(state, that.state) && Objects
      .equals(ranking, that.ranking);
  }

  @Override
  protected int hashCodeOnce() {
    return Objects.hash(state, ranking, volatileIndex);
  }

  @Override
  public String toString() {
    return "|" + state + " :: " + ranking + " :: " + volatileIndex + '|';
  }
}
