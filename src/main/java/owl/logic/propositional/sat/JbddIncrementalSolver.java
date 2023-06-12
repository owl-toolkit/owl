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

package owl.logic.propositional.sat;

import com.google.common.primitives.ImmutableIntArray;
import de.tum.in.jbdd.Bdd;
import de.tum.in.jbdd.BddFactory;
import de.tum.in.jbdd.ImmutableBddConfiguration;

import java.util.*;

public class JbddIncrementalSolver implements IncrementalSolver {

  private final Deque<Integer> clausesStack = new ArrayDeque<>();
  private final Bdd bdd;
  private OptionalInt clauseConjunction;

  JbddIncrementalSolver() {
    bdd = BddFactory.buildBdd(ImmutableBddConfiguration.builder().initialSize(10_000).build());
    clauseConjunction = OptionalInt.of(bdd.trueNode());
  }

  @Override
  public void pushClauses(int... clauses) {
    int conjunction = bdd.trueNode();
    int disjunction = bdd.falseNode();

    int max = 0;

    for (int literal : clauses) {
      max = Math.max(Math.abs(literal), max);
    }

    int requiredVariables = Math.max(max - bdd.numberOfVariables(), 0);

    if (requiredVariables > 0) {
      bdd.createVariables(requiredVariables);
    }

    for (int literal : clauses) {
      if (literal == 0) {
        conjunction = bdd.consume(bdd.and(conjunction, disjunction), conjunction, disjunction);
        disjunction = bdd.falseNode();
        continue;
      }

      if (literal > 0) {
        disjunction = bdd.updateWith(
          bdd.or(disjunction, bdd.variableNode(literal - 1)),
          disjunction);
      } else {
        disjunction = bdd.updateWith(
          bdd.or(disjunction, bdd.not(bdd.variableNode((-literal) - 1))),
          disjunction);
      }
    }

    conjunction = bdd.consume(bdd.and(conjunction, disjunction), conjunction, disjunction);

    if (clauseConjunction.isPresent()) {
      int oldClauseConjunction = clauseConjunction.getAsInt();
      clauseConjunction = OptionalInt.of(bdd.updateWith(
        bdd.and(oldClauseConjunction, conjunction),
        oldClauseConjunction));
    }

    clausesStack.addLast(conjunction);
  }

  @Override
  public void pushClauses(ImmutableIntArray clauses) {
    pushClauses(clauses.toArray());
  }

  @Override
  public void popClauses() {
    bdd.dereference(clausesStack.removeLast());
    clauseConjunction = OptionalInt.empty();
  }

  @Override
  public Optional<BitSet> model() {
    int conjunction = clauseConjunction();

    if (conjunction == bdd.falseNode()) {
      return Optional.empty();
    }

    BitSet model = new BitSet();
    int currentNode = conjunction;

    while (currentNode != bdd.trueNode()) {
      int highNode = bdd.high(currentNode);

      if (highNode == bdd.falseNode()) {
        currentNode = bdd.low(currentNode);
      } else {
        model.set(bdd.variable(currentNode));
        currentNode = highNode;
      }
    }

    return Optional.of(model);
  }

  private int clauseConjunction() {
    if (clauseConjunction.isEmpty()) {
      int conjunction = bdd.trueNode();

      for (int clauses : clausesStack) {
        int newConjunction = bdd.reference(bdd.and(clauses, conjunction));
        bdd.dereference(conjunction);
        conjunction = newConjunction;
      }

      clauseConjunction = OptionalInt.of(conjunction);
    }

    return clauseConjunction.getAsInt();
  }
}
