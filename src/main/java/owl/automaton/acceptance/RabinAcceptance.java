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

package owl.automaton.acceptance;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import owl.logic.propositional.PropositionalFormula;

/**
 * This class represents a Rabin acceptance. It consists of multiple {@link
 * RabinAcceptance.RabinPair}s, which in turn basically comprise a
 * <b>Fin</b> and <b>Inf</b> set. A Rabin pair is accepting, if its <b>Inf</b> set is seen
 * infinitely often <b>and</b> it's <b>Fin</b> set is seen finitely often. The corresponding Rabin
 * acceptance is accepting if <b>any</b> Rabin pair is accepting. Note that therefore a Rabin
 * acceptance without any pairs rejects every word.
 */
public final class RabinAcceptance extends GeneralizedRabinAcceptance {
  private RabinAcceptance(List<RabinPair> pairs) {
    super(List.copyOf(pairs));

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

  public static Optional<RabinAcceptance> ofPartial(PropositionalFormula<Integer> formula) {

    BitSet seenSets = new BitSet();

    for (PropositionalFormula<Integer> pair : PropositionalFormula.disjuncts(formula.nnf())) {
      int fin = Integer.MIN_VALUE;
      int inf = Integer.MIN_VALUE;

      for (PropositionalFormula<Integer> element : PropositionalFormula.conjuncts(pair)) {
        if (element instanceof PropositionalFormula.Variable<Integer> variable) { //  TEMPORAL_INF

          if (inf != Integer.MIN_VALUE) {
            return Optional.empty();
          }

          inf = variable.variable();

        } else if (element instanceof PropositionalFormula.Negation<Integer> negation) { // TEMPORAL_FIN

          if (fin != Integer.MIN_VALUE) {
            return Optional.empty();
          }

          fin = ((PropositionalFormula.Variable<Integer>) negation.operand()).variable();

        } else {
          return Optional.empty();
        }
      }

      // Check that fin is present, even and next to inf.
      if (fin < 0 || fin % 2 != 0 || fin + 1 != inf) {
        return Optional.empty();
      }

      seenSets.set(fin, inf + 1);
    }

    // The set is spare, thus not a proper a Rabin-condition.
    if (seenSets.length() != seenSets.cardinality()) {
      return Optional.empty();
    }

    return Optional.of(RabinAcceptance.of(seenSets.length() / 2));
  }

  @Override
  public String name() {
    return "Rabin";
  }

  @Override
  public List<Object> nameExtra() {
    return List.of(pairs.size());
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
