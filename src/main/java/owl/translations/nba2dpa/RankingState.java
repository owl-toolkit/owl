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

package owl.translations.nba2dpa;

import java.util.List;
import java.util.Set;
import org.immutables.value.Value;
import owl.collections.Collections3;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class RankingState<S> {

  abstract Set<S> initialComponentStates();

  abstract List<S> acceptingComponentStates();

  static <S> RankingState<S> of(Set<S> initialComponentStates, List<S> acceptingComponentStates) {
    assert Collections3.isDistinct(acceptingComponentStates)
      : "The ranking is not distinct: " + acceptingComponentStates;
    return RankingStateTuple.create(initialComponentStates, acceptingComponentStates);
  }

  @Override
  public String toString() {
    return String.format("|%s :: %s|", initialComponentStates(), acceptingComponentStates());
  }
}
