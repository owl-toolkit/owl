/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.extensions.BooleanExpressions;
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

  public static Optional<RabinAcceptance> ofPartial(BooleanExpression<AtomAcceptance> expression) {
    return ofPartial(BooleanExpressions.toPropositionalFormula(expression));
  }

  public static Optional<RabinAcceptance> ofPartial(PropositionalFormula<Integer> formula) {
    return ofPartial(formula, null);
  }

  public static Optional<RabinAcceptance> ofPartial(
    PropositionalFormula<Integer> formula, @Nullable Map<Integer, Integer> mapping) {

    Builder builder = new Builder();

    if (mapping != null) {
      mapping.clear();
    }

    for (PropositionalFormula<Integer> pair : PropositionalFormula.disjuncts(formula)) {
      int fin = -1;
      int inf = -1;

      for (PropositionalFormula<Integer> element : PropositionalFormula.conjuncts(pair)) {

        if (element instanceof PropositionalFormula.Variable) { //  TEMPORAL_INF
          if (inf != -1) {
            return Optional.empty();
          }

          inf = ((PropositionalFormula.Variable<Integer>) element).variable;

        } else if (element instanceof PropositionalFormula.Negation) { // TEMPORAL_FIN
          if (fin != -1) {
            return Optional.empty();
          }

          fin = ((PropositionalFormula.Variable<Integer>)
            ((PropositionalFormula.Negation<Integer>) element).operand).variable;
        } else {
          return Optional.empty();
        }
      }

      if (fin < 0 || inf < 0) {
        return Optional.empty();
      }

      if (mapping == null && (builder.sets != fin || builder.sets + 1 != inf)) {
        return Optional.empty();
      }

      if (mapping != null && (mapping.containsKey(fin) || mapping.containsKey(inf))) {
        return Optional.empty();
      }

      // Record mapping.
      if (mapping != null) {
        mapping.put(fin, builder.sets);
        mapping.put(inf, builder.sets + 1);
      }

      builder.add();
    }

    return Optional.of(builder.build());
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
