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

package owl.translations.rabinizer;

import com.google.auto.value.AutoValue;
import com.google.auto.value.extension.memoized.Memoized;
import com.google.common.collect.Iterables;
import java.util.List;
import owl.ltl.EquivalenceClass;

@AutoValue
public abstract class MonitorState {
  public abstract List<EquivalenceClass> formulaRanking();

  static MonitorState of(EquivalenceClass initialClass) {
    return of(List.of(initialClass));
  }

  static MonitorState of(List<EquivalenceClass> ranking) {
    return new AutoValue_MonitorState(List.copyOf(ranking));
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return String.join("|", Iterables.transform(formulaRanking(), Object::toString));
  }
}
