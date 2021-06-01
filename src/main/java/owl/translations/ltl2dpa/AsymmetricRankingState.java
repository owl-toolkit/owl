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

package owl.translations.ltl2dpa;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import java.util.List;
import owl.automaton.AnnotatedState;
import owl.collections.Collections3;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.AsymmetricProductState;

@AutoValue
public abstract class AsymmetricRankingState implements AnnotatedState<EquivalenceClass> {
  @Override
  public abstract EquivalenceClass state();

  public abstract List<AsymmetricProductState> ranking();

  public abstract int safetyIndex();

  static AsymmetricRankingState of(EquivalenceClass state) {
    return of(state, List.of(), -1);
  }

  static AsymmetricRankingState of(EquivalenceClass state, List<AsymmetricProductState> ranking,
    int safetyProgress) {
    var rankingCopy = List.copyOf(ranking);
    assert Collections3.isDistinct(rankingCopy) : "The ranking is not distinct: " + rankingCopy;
    return new AutoValue_AsymmetricRankingState(state, rankingCopy, safetyProgress);
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return String.format("|%s :: %s :: %s|", state(), ranking(), safetyIndex());
  }
}
