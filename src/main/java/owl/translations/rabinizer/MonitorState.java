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

package owl.translations.rabinizer;

import com.google.common.collect.Iterables;
import java.util.List;
import org.immutables.value.Value;
import owl.ltl.EquivalenceClass;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class MonitorState {
  abstract List<EquivalenceClass> formulaRanking();

  static MonitorState of(EquivalenceClass initialClass) {
    return MonitorStateTuple.create(List.of(initialClass));
  }

  static MonitorState of(List<EquivalenceClass> ranking) {
    return MonitorStateTuple.create(ranking);
  }


  @Override
  public String toString() {
    return String.join("|", Iterables.transform(formulaRanking(), Object::toString));
  }
}
