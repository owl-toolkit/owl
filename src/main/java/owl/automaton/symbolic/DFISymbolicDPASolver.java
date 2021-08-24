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

import static com.google.common.base.Preconditions.checkArgument;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.ATOMIC_PROPOSITION;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.COLOUR;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.STATE;
import static owl.automaton.symbolic.SymbolicAutomaton.VariableType.SUCCESSOR_STATE;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.CONTROLLER;
import static owl.automaton.symbolic.SymbolicDPASolver.Solution.Winner.ENVIRONMENT;

import java.util.Arrays;
import java.util.BitSet;
import java.util.function.IntUnaryOperator;
import owl.automaton.Automaton;
import owl.automaton.acceptance.ParityAcceptance;
import owl.bdd.BddSet;
import owl.collections.BitSet2;
import owl.collections.ImmutableBitSet;

/**
 * This is a symbolic implementation of the explicit fix-point iteration algorithm with freezing
 * as described in https://doi.org/10.4204/EPTCS.305.9.
 * The symbolic implementation is based on http://essay.utwente.nl/81974/ but is adapted
 * to operate on the automaton directly instead of converting to a game first.
 */
public class DFISymbolicDPASolver implements SymbolicDPASolver {

  @Override
  public Solution solve(
    SymbolicAutomaton<? extends ParityAcceptance> dpa,
    ImmutableBitSet controlledAps) {

    checkArgument(dpa.acceptance().parity() == ParityAcceptance.Parity.MIN_EVEN,
      dpa.acceptance().parity());
    checkArgument(controlledAps.size() <= dpa.atomicPropositions().size());
    checkArgument(dpa.is(Automaton.Property.COMPLETE));
    checkArgument(dpa.is(Automaton.Property.DETERMINISTIC));
    var factory = dpa.factory();
    ImmutableBitSet states = dpa.variableAllocation().variables(STATE);
    ImmutableBitSet successorStates = dpa.variableAllocation().variables(SUCCESSOR_STATE);

    BitSet controlledApsBitset = controlledAps.copyInto(new BitSet());
    BitSet uncontrolledAps = BitSet2.copyOf(controlledAps);
    uncontrolledAps.flip(0, dpa.variableAllocation().variables(ATOMIC_PROPOSITION).size());

    BitSet distractionsControllerProjectionSet = successorStates.copyInto(new BitSet());
    distractionsControllerProjectionSet.or(
      dpa.variableAllocation().localToGlobal(controlledApsBitset, ATOMIC_PROPOSITION)
    );
    distractionsControllerProjectionSet.or(dpa.variableAllocation().variables(COLOUR)
      .copyInto(new BitSet()));

    IntUnaryOperator stateToSuccessor = (variable) -> {
      if (states.contains(variable)) {
        return dpa.variableAllocation()
          .localToGlobal(dpa.variableAllocation().globalToLocal(variable, STATE),
            SUCCESSOR_STATE);
      } else if (successorStates.contains(variable)) {
        return dpa.variableAllocation()
          .localToGlobal(dpa.variableAllocation().globalToLocal(variable, SUCCESSOR_STATE),
            STATE);
      } else {
        return variable;
      }
    };
    BddSet reachableStates = dpa.reachableStates();
    int maxPriority = dpa.acceptance().acceptanceSets()
      + (1 - (dpa.acceptance().acceptanceSets() % 2));
    assert maxPriority % 2 == 1;

    // Initialize variables
    BddSet[] frozenController = new BddSet[maxPriority + 1];
    Arrays.fill(frozenController, factory.of(false));
    BddSet[] frozenEnvironment = new BddSet[maxPriority + 1];
    Arrays.fill(frozenEnvironment, factory.of(false));
    BddSet distractionsController = factory.of(false);
    BddSet distractionsEnvironment = factory.of(false);
    BddSet strategyController = factory.of(false);
    BddSet strategyEnvironment = factory.of(false);

    // Precompute commonly needed BDDs
    BddSet[] statesOfPriority = statesOfPriority(dpa);
    BddSet[] statesOfHigherPriority = statesOfHigherPriority(dpa, statesOfPriority);
    BddSet statesOfEvenPriority = statesOfEvenPriority(dpa, statesOfPriority);

    int priority = maxPriority;
    while (priority >= 0) {
      boolean hasControllerParity = dpa.acceptance().parity().isAccepting(priority);
      BddSet nonFrozenDistractionFreeStatesController = priority == maxPriority
        ? reachableStates
        .intersection(
          factory.union(frozenController).complement(),
          distractionsController.complement()
        )
        : factory.of(false);

      BddSet nonFrozenDistractionFreeStatesEnvironment = statesOfPriority[priority]
        .intersection(
          factory.union(frozenEnvironment).complement(),
          distractionsEnvironment.complement(),
          reachableStates
        );

      // All non-frozen distraction-free controller states that can reach an environment state
      // of even in one step
      BddSet newDistractionsController = nonFrozenDistractionFreeStatesController.intersection(
        even(dpa, distractionsEnvironment, statesOfEvenPriority)
          .relabel(stateToSuccessor)
          .intersection(dpa.transitionRelation())
          .project(distractionsControllerProjectionSet)
      );

      // All non-frozen distraction-free environment states that must enter a controller distraction
      BddSet newDistractionsEnvironment = nonFrozenDistractionFreeStatesEnvironment.intersection(
        dpa.transitionRelation()
          .intersection(distractionsController.project(dpa.variableAllocation().variables(COLOUR))
            .complement()
          )
          .project(dpa.variableAllocation().variables(ATOMIC_PROPOSITION, COLOUR, SUCCESSOR_STATE))
          .complement()
      );

      if (hasControllerParity) {
        newDistractionsController = newDistractionsController
          .complement()
          .intersection(nonFrozenDistractionFreeStatesController);
        newDistractionsEnvironment = newDistractionsEnvironment
          .complement()
          .intersection(nonFrozenDistractionFreeStatesEnvironment);
      }

      BddSet oldDistractionsController = distractionsController;
      BddSet oldDistractionsEnvironment = distractionsEnvironment;

      distractionsController = distractionsController.union(newDistractionsController);
      distractionsEnvironment = distractionsEnvironment.union(newDistractionsEnvironment);

      // Update non-frozen distraction-free controller states to move to an even environment state
      // , if possible
      strategyController = strategyController
        .intersection(nonFrozenDistractionFreeStatesController
          .project(dpa.variableAllocation().variables(COLOUR))
          .complement()
        )
        .union(nonFrozenDistractionFreeStatesController
          .project(dpa.variableAllocation().variables(COLOUR))
          .intersection(
            dpa.transitionRelation(),
            even(dpa, distractionsEnvironment, statesOfEvenPriority)
              .relabel(stateToSuccessor)
              .project(dpa.variableAllocation().variables(STATE))
          )
        );

      // Update non-frozen distraction-free environment states to move to a distraction-free
      // controller state, if possible
      strategyEnvironment = strategyEnvironment
        .intersection(nonFrozenDistractionFreeStatesEnvironment
          .project(dpa.variableAllocation().variables(COLOUR))
          .complement()
        )
        .union(nonFrozenDistractionFreeStatesEnvironment
          .project(dpa.variableAllocation().variables(COLOUR))
          .intersection(
            dpa.transitionRelation(),
            distractionsController.complement()
          )
          .project(distractionsControllerProjectionSet)
        );
      if (
        oldDistractionsEnvironment.equals(distractionsEnvironment)
          && oldDistractionsController.equals(distractionsController)
      ) {
        frozenController[priority] = priority == maxPriority
          ? frozenController[priority]
          : factory.of(false);
        frozenEnvironment[priority] = frozenEnvironment[priority]
          .intersection(statesOfHigherPriority[priority].complement());
        priority--;
      } else {
        BddSet nonFrozenStatesWithHigherPriorityController = priority == maxPriority
          ? factory.of(false)
          : reachableStates.intersection(factory.union(frozenController).complement());

        BddSet wController = nonFrozenStatesWithHigherPriorityController.intersection(
          hasControllerParity ? distractionsController : distractionsController.complement()
        );

        frozenController[priority] = frozenController[priority].union(
          nonFrozenStatesWithHigherPriorityController.intersection(wController.complement())
        );

        BddSet nonFrozenStatesWithHigherPriorityEnvironment = statesOfHigherPriority[priority]
          .intersection(
            factory.union(frozenEnvironment).complement(),
            reachableStates
          );

        BddSet wEnvironment = nonFrozenStatesWithHigherPriorityEnvironment.intersection(
          hasControllerParity
            ? even(dpa, distractionsEnvironment, statesOfEvenPriority)
            : odd(dpa, distractionsEnvironment, statesOfEvenPriority)
        );

        frozenEnvironment[priority] = frozenEnvironment[priority].union(
          nonFrozenStatesWithHigherPriorityEnvironment.intersection(wEnvironment.complement())
        );

        distractionsController = distractionsController.intersection(wController.complement());
        distractionsEnvironment = distractionsEnvironment.intersection(wEnvironment.complement());

        priority = maxPriority;
      }
    }
    BddSet winningRegionController = even(dpa, distractionsEnvironment, statesOfEvenPriority);
    if (winningRegionController.containsAll(dpa.initialStates().intersection(
      factory.of(new BitSet(), dpa.variableAllocation().variables(COLOUR)))
    )) {
      // Initial state is won by controller, return strategy
      return Solution.of(CONTROLLER, winningRegionController, strategyController);
    } else {
      // Initial state is won by environment, return counter-strategy
      BddSet winningRegionEnvironment = odd(dpa, distractionsController, statesOfEvenPriority);
      return Solution.of(ENVIRONMENT, winningRegionEnvironment, strategyEnvironment);
    }
  }

  private static BddSet even(
    SymbolicAutomaton<? extends ParityAcceptance> automaton,
    BddSet distractions,
    BddSet statesOfEvenPriority
  ) {
    // (statesOfEvenPriority && !distractions || !statesOfEvenPriority && distractions)
    return statesOfEvenPriority
      .intersection(distractions.complement())
      .union(statesOfEvenPriority.complement().intersection(distractions))
      .intersection(automaton.reachableStates());
  }

  private static BddSet odd(
    SymbolicAutomaton<? extends ParityAcceptance> automaton,
    BddSet distractions,
    BddSet statesOfEvenPriority
  ) {
    // (statesOfEvenPriority && distractions || !statesOfEvenPriority && !distractions)
    return statesOfEvenPriority
      .intersection(distractions)
      .union(statesOfEvenPriority.union(distractions).complement())
      .intersection(automaton.reachableStates());
  }

  private static BddSet statesOfEvenPriority(
    SymbolicAutomaton<? extends ParityAcceptance> automaton,
    BddSet[] statesOfPriority
  ) {
    BddSet[] statesOfEvenPriority = new BddSet[statesOfPriority.length / 2];
    for (int i = 0; i < statesOfEvenPriority.length; i++) {
      statesOfEvenPriority[i] = statesOfPriority[2 * i];
    }
    return automaton.factory().union(statesOfEvenPriority);
  }

  private static BddSet[] statesOfPriority(
    SymbolicAutomaton<? extends ParityAcceptance> automaton
  ) {
    int maxPriority = automaton.acceptance().acceptanceSets()
      + (1 - (automaton.acceptance().acceptanceSets() % 2));
    BddSet[] prioritySets = new BddSet[maxPriority + 1];
    for (int i = 0; i < automaton.acceptance().acceptanceSets(); i++) {
      prioritySets[i] = automaton.factory().of(
        automaton.variableAllocation().localToGlobal(i + automaton.colourOffset(), COLOUR)
      );
    }
    if ((automaton.acceptance().acceptanceSets() % 2) == 0) {
      prioritySets[maxPriority - 1] = automaton.factory().of(false);
    }
    BitSet colours = new BitSet();
    colours.set(
      automaton.colourOffset(),
      automaton.acceptance().acceptanceSets() + automaton.colourOffset()
    );
    colours = automaton.variableAllocation().localToGlobal(colours, COLOUR);
    prioritySets[maxPriority] = automaton.factory().of(new BitSet(), colours);
    return prioritySets;
  }

  private static BddSet[] statesOfHigherPriority(
    SymbolicAutomaton<? extends ParityAcceptance> automaton,
    BddSet[] statesOfPriority
  ) {
    int maxPriority = automaton.acceptance().acceptanceSets()
      + (1 - (automaton.acceptance().acceptanceSets() % 2));
    BddSet[] statesOfHigherPriority = new BddSet[maxPriority + 1];
    statesOfHigherPriority[maxPriority] = automaton.factory().of(false);
    for (int i = maxPriority - 1; i >= 0; i--) {
      statesOfHigherPriority[i] = statesOfHigherPriority[i + 1].union(statesOfPriority[i + 1]);
    }
    return statesOfHigherPriority;
  }
}
