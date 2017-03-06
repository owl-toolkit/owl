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

package owl.automaton.acceptance;

import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.edge.Edge;

public class BuchiAcceptance extends GeneralizedBuchiAcceptance {

  public BuchiAcceptance() {
    super(1);
  }

  @Override
  public String getName() {
    return "Buchi";
  }

  @Override
  public List<Object> getNameExtra() {
    return Collections.emptyList();
  }

  @Override
  public <S> boolean isAccepting(Set<S> scc, Function<S, Iterable<Edge<S>>> successorFunction) {
    return scc.parallelStream()
      .map(successorFunction)
      .flatMap(Streams::stream)
      .anyMatch(edge -> scc.contains(edge.getSuccessor()) && edge.inSet(0));
  }
}
