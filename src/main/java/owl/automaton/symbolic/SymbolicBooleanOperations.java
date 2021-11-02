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

package owl.automaton.symbolic;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.logic.propositional.PropositionalFormula;

/**
 * Boolean operations on symbolic automata.
 *
 * <p>See for an equivalent on explicit automata, see {@link owl.automaton.BooleanOperations}.
 */
public final class SymbolicBooleanOperations {

  private SymbolicBooleanOperations() {}

  public static SymbolicAutomaton<?> deterministicUnion(SymbolicAutomaton<?>... automata) {
    return deterministicUnion(List.of(automata));
  }

  public static SymbolicAutomaton<?> deterministicUnion(
    List<? extends SymbolicAutomaton<?>> automata) {

    return deterministicProduct(
      PropositionalFormula.Disjunction.of(
        IntStream.range(0, automata.size())
          .mapToObj(PropositionalFormula.Variable::of)
          .toList()
      ), automata);
  }

  public static SymbolicAutomaton<?> intersection(SymbolicAutomaton<?>... automata) {
    return intersection(List.of(automata));
  }

  public static SymbolicAutomaton<?> intersection(List<? extends SymbolicAutomaton<?>> automata) {
    return deterministicProduct(
      PropositionalFormula.Conjunction.of(
        IntStream.range(0, automata.size())
          .mapToObj(PropositionalFormula.Variable::of)
          .toList()
      ), automata);
  }

  public static SymbolicAutomaton<?> deterministicStructureProduct(
    SymbolicAutomaton<?> automaton1, SymbolicAutomaton<?> automaton2
  ) {
    return deterministicProduct(
      PropositionalFormula.Variable.of(0),
      List.of(automaton1, automaton2)
    );
  }

  public static SymbolicAutomaton<?> deterministicProduct(
    PropositionalFormula<Integer> automatonFormula, List<? extends SymbolicAutomaton<?>> automata) {

    Preconditions.checkArgument(
      IntStream.range(0, automata.size())
        .boxed()
        .collect(Collectors.toSet())
        .containsAll(automatonFormula.variables())
    );

    Preconditions.checkArgument(!automata.isEmpty());

    for (SymbolicAutomaton<?> symbolicAutomaton : automata) {
      Preconditions.checkArgument(
        symbolicAutomaton.is(Automaton.Property.COMPLETE));
      Preconditions.checkArgument(
        symbolicAutomaton.atomicPropositions().equals(automata.get(0).atomicPropositions()));
      Preconditions.checkArgument(
        symbolicAutomaton.factory().equals(automata.get(0).factory()));
    }

    var allocationCombiner = new SequentialVariableAllocationCombiner(
      automata.stream().map(SymbolicAutomaton::variableAllocation).toList()
    );

    BddSetFactory bddSetFactory = automata.get(0).factory();

    BddSet productInitialStates = bddSetFactory.of(true);
    BddSet productTransitionRelation = bddSetFactory.of(true);

    for (SymbolicAutomaton<?> automaton : automata) {
      productInitialStates = productInitialStates.intersection(
        automaton.initialStates().relabel(
          i -> allocationCombiner.localToGlobal(i, automaton.variableAllocation())
        )
      );

      productTransitionRelation = productTransitionRelation.intersection(
        automaton.transitionRelation().relabel(
          i -> allocationCombiner.localToGlobal(i, automaton.variableAllocation())
        )
      );
    }

    var colourOffsets = new int[automata.size()];
    int currentOffset = 0;
    for (int i = 0; i < automata.size(); i++) {
      colourOffsets[i] = currentOffset;
      currentOffset += automata.get(i).acceptance().acceptanceSets();
    }
    var productAcceptance = EmersonLeiAcceptance.of(
      automatonFormula.substitute(
        automatonIndex -> automata.get(automatonIndex).acceptance().booleanExpression()
          .map(i -> colourOffsets[automatonIndex] + i)
      )
    );

    // TODO check semi-deterministic and limit-deterministic property
    Set<Automaton.Property> properties = EnumSet.of(Automaton.Property.COMPLETE);
    if (automata.stream().allMatch(automaton -> automaton.is(Automaton.Property.DETERMINISTIC))) {
      properties.add(Automaton.Property.DETERMINISTIC);
    }

    return SymbolicAutomaton.of(
      automata.get(0).atomicPropositions(),
      productInitialStates,
      productTransitionRelation,
      productAcceptance,
      allocationCombiner,
      properties
    );
  }
}
