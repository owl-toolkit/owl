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

package owl.translations.ldba;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import omega_automaton.Automaton;
import omega_automaton.AutomatonState;
import omega_automaton.acceptance.GeneralisedBuchiAcceptance;
import omega_automaton.algorithms.SCCAnalyser;
import omega_automaton.collections.valuationset.ValuationSet;
import omega_automaton.output.HOAConsumerExtended;
import omega_automaton.output.HOAPrintable;
import owl.automaton.edge.Edge;
import owl.translations.Optimisation;

public class LimitDeterministicAutomaton<S_I extends AutomatonState<S_I>, S_A extends AutomatonState<S_A>, Acc extends GeneralisedBuchiAcceptance, I extends AbstractInitialComponent<S_I, S_A>, A extends Automaton<S_A, Acc>>
  implements HOAPrintable {

  private final A acceptingComponent;
  private final I initialComponent;
  private final Set<? extends AutomatonState<?>> initialStates;
  private final EnumSet<Optimisation> optimisations;

  public LimitDeterministicAutomaton(I initialComponent, A acceptingComponent,
    Set<? extends AutomatonState<?>> initialStates, EnumSet<Optimisation> optimisations) {
    this.initialComponent = initialComponent;
    this.acceptingComponent = acceptingComponent;
    this.optimisations = optimisations;
    this.initialStates = initialStates;
  }

  public void generate() {
    initialComponent.generate();

    // Generate Jump Table
    List<Set<S_I>> sccs = optimisations.contains(Optimisation.SCC_ANALYSIS) ?
                          SCCAnalyser.SCCsStates(initialComponent) :
                          Collections.singletonList(initialComponent.getStates());

    for (Set<S_I> scc : sccs) {
      // Skip non-looping states with successors of a singleton SCC.
      if (scc.size() == 1) {
        S_I state = Iterables.getOnlyElement(scc);

        if (initialComponent.isTransient(state) && initialComponent.hasSuccessors(state)) {
          continue;
        }
      }

      for (S_I state : scc) {
        initialComponent.generateJumps(state);
      }
    }

    acceptingComponent.generate();

    if (optimisations.contains(Optimisation.REMOVE_EPSILON_TRANSITIONS)) {
      Set<S_A> accReach = new HashSet<>(
        (Set<S_A>) Sets.intersection(initialStates, acceptingComponent.getStates()));

      for (S_I state : initialComponent.getStates()) {
        Map<Edge<S_I>, ValuationSet> successors = initialComponent.getSuccessors(state);
        Map<ValuationSet, Set<S_A>> successorJumps = initialComponent.valuationSetJumps.row(state);

        successors.forEach((successor, vs) -> {
          // Copy successors to a new collection, since clear() will also empty these collections.
          Set<S_A> targets = new HashSet<>(
            initialComponent.epsilonJumps.get(successor.getSuccessor()));
          accReach.addAll(targets);

          // Non-determinism!
          Set<S_A> oldTargets = successorJumps.put(vs, targets);
          if (oldTargets != null) {
            targets.addAll(oldTargets);
          }
        });
      }

      initialComponent.epsilonJumps.clear();
      initialComponent.removeUnreachableStates(
        new HashSet<>((Set<S_I>) Sets.intersection(initialStates, initialComponent.getStates())));
      acceptingComponent.removeUnreachableStates(accReach);
      initialComponent.removeDeadEnds(initialComponent.valuationSetJumps.rowKeySet());
      initialStates.removeIf(
        x -> !Sets.union(initialComponent.getStates(), acceptingComponent.getStates()).contains(x));
    }
  }

  public A getAcceptingComponent() {
    return acceptingComponent;
  }

  public I getInitialComponent() {
    return initialComponent;
  }

  public boolean isDeterministic() {
    return initialComponent.size() == 0;
  }

  @Override
  public void setAtomMapping(Map<Integer, String> mapping) {
    acceptingComponent.setAtomMapping(mapping);
  }

  public int size() {
    return acceptingComponent.size() + initialComponent.size();
  }

  @Override
  public void toHOA(HOAConsumer c, EnumSet<Option> options) {
    HOAConsumerExtended consumer = new HOAConsumerExtended(c,
      acceptingComponent.getFactory().getSize(), acceptingComponent.getAtomMapping(),
      acceptingComponent.getAcceptance(), initialStates, size(), options);
    initialComponent.toHOABody(consumer);
    acceptingComponent.toHOABody(consumer);
    consumer.done();
  }

  @Override
  public String toString() {
    try {
      return toString(EnumSet.allOf(Option.class));
    } catch (IOException ex) {
      throw new IllegalStateException(ex.toString(), ex);
    }
  }

  public String toString(EnumSet<Option> options) throws IOException {
    try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
      toHOA(new HOAConsumerPrint(stream), options);
      return stream.toString("UTF8");
    }
  }
}
