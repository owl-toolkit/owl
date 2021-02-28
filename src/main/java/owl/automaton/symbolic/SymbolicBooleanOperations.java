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

package owl.automaton.symbolic;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.bdd.BddSet;
import owl.bdd.FactorySupplier;
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
          .collect(Collectors.toList())
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
          .collect(Collectors.toList())
      ), automata);
  }

  public static SymbolicAutomaton<?> deterministicProduct(
    PropositionalFormula<Integer> automatonFormula, List<? extends SymbolicAutomaton<?>> automata) {

    Preconditions.checkArgument(
      automatonFormula
        .variables()
        .equals(
          IntStream.range(0, automata.size())
            .boxed()
            .collect(Collectors.toSet())
        )
    );

    Preconditions.checkArgument(!automata.isEmpty()
      && automata.stream().allMatch(automaton -> automaton.is(Automaton.Property.COMPLETE)
      && automaton.atomicPropositions().equals(automata.get(0).atomicPropositions())
    ));

    var allocationCombiner = new SequentialVariableAllocationCombiner(
      automata.stream().map(SymbolicAutomaton::variableAllocation).collect(Collectors.toList())
    );

    var bddSetFactory = FactorySupplier.defaultSupplier()
      .getBddSetFactory(allocationCombiner.variableNames());

    BddSet productInitialStates = bddSetFactory.universe();
    BddSet productTransitionRelation = bddSetFactory.universe();

    for (SymbolicAutomaton<?> automaton : automata) {
      productInitialStates = productInitialStates.intersection(
        automaton.initialStates().transferTo(
          bddSetFactory, i -> allocationCombiner.localToGlobal(i, automaton.variableAllocation())
        )
      );

      productTransitionRelation = productTransitionRelation.intersection(
        automaton.transitionRelation().transferTo(
          bddSetFactory, i -> allocationCombiner.localToGlobal(i, automaton.variableAllocation())
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
      automatonFormula.substituteTo(
        automatonIndex -> automata.get(automatonIndex).acceptance().booleanExpression().substitute(
          i -> Optional.of(PropositionalFormula.Variable.of(colourOffsets[automatonIndex] + i))
        )
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
