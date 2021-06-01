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
import java.util.List;
import owl.ltl.EquivalenceClass;

@AutoValue
public abstract class RabinizerState {
  public abstract EquivalenceClass masterState();

  public abstract List<MonitorState> monitorStates();

  static RabinizerState of(EquivalenceClass masterState, MonitorState[] monitorStates) {
    return of(masterState, List.of(monitorStates));
  }

  static RabinizerState of(EquivalenceClass masterState, List<MonitorState> monitorStates) {
    return new AutoValue_RabinizerState(masterState, List.copyOf(monitorStates));
  }

  @Override
  public abstract boolean equals(Object object);

  @Memoized
  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(50 * (1 + monitorStates().size()));
    builder.append("<<").append(masterState());
    for (MonitorState monitorState : monitorStates()) {
      builder.append("::").append(monitorState);
    }
    builder.append(">>");
    return builder.toString();
  }
}
