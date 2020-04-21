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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.List;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
import owl.automaton.edge.Edge;

/**
 * This class represents a Rabin acceptance. It consists of multiple {@link
 * RabinAcceptance.RabinPair}s, which in turn basically comprise a (potentially lazily allocated)
 * <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if it's <b>Inf</b> set is seen
 * infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding Rabin
 * acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public final class RabinAcceptance extends GeneralizedRabinAcceptance {
  private RabinAcceptance(List<RabinPair> pairs) {
    super(pairs);

    // Check consistency.
    checkArgument(acceptanceSets() == 2 * this.pairs.size());
    for (RabinPair pair : this.pairs) {
      checkArgument(pair.finSet() + 1 == pair.infSet());
    }
  }

  public static RabinAcceptance of(int count) {
    List<RabinPair> pairs = new ArrayList<>(count);
    for (int index = 0; index < count; index++) {
      pairs.add(RabinPair.of(index * 2));
    }
    return new RabinAcceptance(pairs);
  }

  public static RabinAcceptance of(List<RabinPair> pairs) {
    return new RabinAcceptance(pairs);
  }

  public static RabinAcceptance of(RabinPair... pairs) {
    return of(List.of(pairs));
  }

  public static RabinAcceptance of(BooleanExpression<AtomAcceptance> expression) {
    Builder builder = new Builder();

    int setCount = 0;
    for (BooleanExpression<AtomAcceptance> pair : BooleanExpressions.getDisjuncts(expression)) {
      int fin = -1;
      int inf = -1;

      for (BooleanExpression<AtomAcceptance> element : BooleanExpressions.getConjuncts(pair)) {
        AtomAcceptance atom = element.getAtom();

        switch (atom.getType()) {
          case TEMPORAL_FIN:
            checkArgument(fin == -1);
            fin = atom.getAcceptanceSet();
            checkArgument(fin == setCount);
            setCount++;
            break;

          case TEMPORAL_INF:
            checkArgument(inf == -1);
            inf = atom.getAcceptanceSet();
            checkArgument(inf == setCount);
            setCount++;
            break;

          default:
            throw new IllegalArgumentException("Rabin Acceptance not well-formed.");
        }
      }

      checkArgument(fin >= 0);
      checkArgument(inf >= 0);
      builder.add();
    }

    return builder.build();
  }

  @Override
  public String name() {
    return "Rabin";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(pairs.size());
  }

  @Override
  public boolean isWellFormedEdge(Edge<?> edge) {
    return edge.largestAcceptanceSet() < 2 * pairs.size();
  }

  public static final class Builder {
    private final List<RabinPair> pairs = new ArrayList<>(); // NOPMD
    private int sets = 0;

    public RabinPair add() {
      RabinPair pair = new RabinPair(sets, sets + 1);
      pairs.add(pair);
      sets += 2;
      return pair;
    }

    public RabinAcceptance build() {
      return of(pairs);
    }
  }
}
