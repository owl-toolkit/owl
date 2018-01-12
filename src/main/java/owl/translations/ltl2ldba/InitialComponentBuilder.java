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

package owl.translations.ltl2ldba;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration.NON_DETERMINISTIC_INITIAL_COMPONENT;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.ltl2ldba.AnalysisResult.TYPE;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public class InitialComponentBuilder<K extends RecurringObligation>
  implements MutableAutomatonBuilder<EquivalenceClass, EquivalenceClass, NoneAcceptance> {

  private final boolean constructDeterministic;
  private final Deque<EquivalenceClass> constructionQueue;
  private final Factories factories;
  private final EquivalenceClassStateFactory factory;
  private final AbstractJumpManager<K> jumpFactory;
  private final SetMultimap<EquivalenceClass, Jump<K>> jumps;
  private final Set<EquivalenceClass> patientStates;

  InitialComponentBuilder(Factories factories, ImmutableSet<Configuration> configuration,
    AbstractJumpManager<K> jumpFactory) {
    this.factories = factories;
    this.jumpFactory = jumpFactory;

    factory = new EquivalenceClassStateFactory(factories.eqFactory, configuration);
    constructionQueue = new ArrayDeque<>();
    constructDeterministic = !configuration.contains(NON_DETERMINISTIC_INITIAL_COMPONENT);

    jumps = MultimapBuilder.hashKeys().hashSetValues().build();
    patientStates = new HashSet<>();
  }

  @Nullable
  @Override
  public EquivalenceClass add(EquivalenceClass initialClass) {
    if (initialClass.isFalse()) {
      return null;
    }

    EquivalenceClass state = factory.getInitial(initialClass);
    constructionQueue.add(state);
    return state;
  }

  @Override
  public MutableAutomaton<EquivalenceClass, NoneAcceptance> build() {
    MutableAutomaton<EquivalenceClass, NoneAcceptance> automaton =
      MutableAutomatonFactory
        .createMutableAutomaton(NoneAcceptance.INSTANCE, factories.vsFactory);

    if (constructDeterministic) {
      AutomatonUtil.exploreDeterministic(automaton, constructionQueue,
        this::getDeterministicSuccessor, factory::getSensitiveAlphabet);
    } else {
      AutomatonUtil.explore(automaton, constructionQueue, this::getNondeterministicSuccessors,
        factory::getSensitiveAlphabet);
    }

    automaton.setInitialStates(constructionQueue);

    return automaton;
  }

  private void generateJumps(EquivalenceClass state) {
    if (jumps.containsKey(state)) {
      return;
    }

    AnalysisResult<K> result = jumpFactory.analyse(state);
    jumps.putAll(state, result.jumps);

    if (result.type == TYPE.MAY) {
      patientStates.add(state);
    }
  }

  @Nullable
  private Edge<EquivalenceClass> getDeterministicSuccessor(EquivalenceClass state,
    BitSet valuation) {
    EquivalenceClass successor = factory.getSuccessor(state, valuation);

    generateJumps(state);

    if (successor.isTrue()) {
      return Edge.of(successor, 0);
    }

    // Suppress edge, if successor is a non-accepting state or this state is impatient (e.g. G a)
    if (successor.isFalse() || !patientStates.contains(state)) {
      return null;
    }

    return Edge.of(successor);
  }

  Set<Jump<K>> getJumps(EquivalenceClass state) {
    return jumps.get(state);
  }

  private Iterable<Edge<EquivalenceClass>> getNondeterministicSuccessors(EquivalenceClass state,
    BitSet valuation) {
    EquivalenceClass successorClass = factory.getNondetSuccessor(state, valuation);

    generateJumps(state);

    if (successorClass.isTrue()) {
      return Set.of(Edge.of(successorClass, 0));
    }

    // Suppress edge, if successor is a non-accepting state or this state is impatient (e.g. G a)
    if (successorClass.isFalse() || !patientStates.contains(state)) {
      return List.of();
    }

    // Split successor
    List<Edge<EquivalenceClass>> successors = new ArrayList<>();
    factory.splitEquivalenceClass(successorClass).forEach(x -> successors.add(Edge.of(x)));
    return successors;
  }
}
