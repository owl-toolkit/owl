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

package owl.game;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.collections.Collections3;
import owl.game.output.AigConsumer;
import owl.game.output.AigFactory;
import owl.game.output.AigPrintable;
import owl.game.output.LabelledAig;

public interface Game<S, A extends EmersonLeiAcceptance> extends Automaton<S, A>, AigPrintable {

  default Set<S> getAttractor(Collection<S> states, Owner owner) {
    // Does not contain the states itself.
    Set<S> attractor = new HashSet<>();

    // Add states that owner controls;
    for (S predecessor : predecessors(states)) {
      if (owner == owner(predecessor) || states.containsAll(successors(predecessor))) {
        attractor.add(predecessor);
      }
    }

    return attractor;
  }

  default Set<S> getAttractorFixpoint(Collection<S> states, Owner owner) {
    Set<S> attractor = new HashSet<>(states);
    boolean continueIteration = true;

    while (continueIteration) {
      continueIteration = attractor.addAll(getAttractor(attractor, owner));
    }

    return attractor;
  }

  Owner owner(S state);

  default Set<S> states(Owner owner) {
    return Sets.filter(states(), x -> owner(x) == owner);
  }

  BitSet choice(S state, Owner owner);

  @Override
  default void feedTo(AigConsumer consumer) {
    List<String> inputNames = variables(Owner.PLAYER_1);
    List<String> outputNames = variables(Owner.PLAYER_2);

    AigFactory factory = new AigFactory();
    inputNames.forEach(consumer::addInput);

    // how many latches will we need?
    int nStates = states(Owner.PLAYER_2).size();
    int nLatches = (int) Math.ceil(Math.log(nStates) / Math.log(2));

    // create mapping from states to bitsets of latches + inputs
    // where the input bits are always set to 0
    Map<S, BitSet> encoding = new HashMap<>();
    int iState = inputNames.size() + 1;

    for (S state : states(Owner.PLAYER_2)) {
      int value = iState;
      int index = inputNames.size();
      BitSet b = new BitSet(inputNames.size() + nLatches);
      while (value != 0) {
        if (value % 2 != 0) {
          b.set(index);
        }
        index++;
        value >>>= 1;
      }
      encoding.put(state, b);
      iState += 1;
    }

    // create a list of LabelledAig for the latches and outputs
    List<LabelledAig> latches = new ArrayList<>(
      Collections.nCopies(nLatches, factory.getFalse()));
    List<LabelledAig> outputs = new ArrayList<>(
      Collections.nCopies(outputNames.size(), factory.getFalse()));

    // iterate through labelled edges to create latch and output formulas
    for (S player2State : states(Owner.PLAYER_2)) {
      BitSet stateAndInput = owl.collections.BitSet2.copyOf(encoding.get(player2State));
      stateAndInput.or(choice(player2State, Owner.PLAYER_1));
      LabelledAig stateAndInputAig = factory.cube(stateAndInput);

      // for all set indices in the output valuation
      // we update their transition function
      choice(player2State, Owner.PLAYER_2).stream().forEach(
        i -> outputs.set(i, factory.disjunction(outputs.get(i), stateAndInputAig)));

      // we do the same for all set indices in the representation
      // of the successor state
      encoding.get(Iterables.getOnlyElement(successors(player2State))).stream().forEach(
        i -> latches.set(i, factory.disjunction(latches.get(i), stateAndInputAig)));
    }

    // we finish adding the information to the consumer
    for (LabelledAig a : latches) {
      consumer.addLatch("", a);
    }

    Collections3.forEachPair(outputNames, outputs, consumer::addOutput);
  }

  default Set<S> predecessors(S state, Owner owner) {
    return predecessors(Set.of(state), owner);
  }

  default Set<S> predecessors(Iterable<S> states) {
    Set<S> predecessors = new HashSet<>();
    states.forEach(x -> predecessors.addAll(predecessors(x)));
    return predecessors;
  }

  default Set<S> predecessors(Iterable<S> state, Owner owner) {
    return Sets.filter(predecessors(state), x -> owner == owner(x));
  }

  default Set<S> successors(S state, Owner owner) {
    return successors(Set.of(state), owner);
  }

  default Set<S> successors(Iterable<S> states) {
    Set<S> successors = new HashSet<>();
    states.forEach(x -> successors.addAll(successors(x)));
    return successors;
  }

  default Set<S> successors(Iterable<S> states, Owner owner) {
    return Sets.filter(successors(states), x -> owner == owner(x));
  }

  List<String> variables(Owner owner);

  enum Owner {
    /**
     * This player wants to dissatisfy the acceptance condition.
     */
    PLAYER_1,
    /**
     * This player wants to satisfy the acceptance condition.
     */
    PLAYER_2;

    public Owner opponent() {
      return this == PLAYER_1 ? PLAYER_2 : PLAYER_1;
    }

    public boolean isEven() {
      return PLAYER_2 == this;
    }

    public boolean isOdd() {
      return PLAYER_1 == this;
    }
  }
}
