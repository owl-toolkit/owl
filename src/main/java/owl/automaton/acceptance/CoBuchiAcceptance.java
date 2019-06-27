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

package owl.automaton.acceptance;

import java.util.BitSet;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.automaton.edge.Edge;

public final class CoBuchiAcceptance extends OmegaAcceptance {
  public static final CoBuchiAcceptance INSTANCE = new CoBuchiAcceptance();

  @Override
  public int acceptanceSets() {
    return 1;
  }

  @Override
  public BooleanExpression<AtomAcceptance> booleanExpression() {
    return BooleanExpressions.mkFin(0);
  }

  @Override
  public String name() {
    return "co-Buchi";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of();
  }

  @Override
  public BitSet acceptingSet() {
    return new BitSet();
  }

  @Override
  public BitSet rejectingSet() {
    BitSet set = new BitSet();
    set.set(0);
    return set;
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() <= 0;
  }
}
