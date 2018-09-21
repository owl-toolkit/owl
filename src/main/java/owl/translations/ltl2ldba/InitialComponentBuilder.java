/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.ImplicitNonDeterministicEdgeTreeAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonFactory;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.MutableAutomatonBuilder;
import owl.collections.ValuationTree;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.translations.canonical.LegacyFactory;
import owl.translations.ltl2ldba.AnalysisResult.TYPE;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

class InitialComponentBuilder<K extends RecurringObligation>
  implements MutableAutomatonBuilder<EquivalenceClass, EquivalenceClass, NoneAcceptance> {

  private final Deque<EquivalenceClass> constructionQueue;
  private final Factories factories;
  private final LegacyFactory factory;
  private final AbstractJumpManager<K> jumpFactory;
  private final SetMultimap<EquivalenceClass, Jump<K>> jumps;
  private final Set<EquivalenceClass> patientStates;

  InitialComponentBuilder(Factories factories, Set<Configuration> configuration,
    AbstractJumpManager<K> jumpFactory) {
    this.factories = factories;
    this.jumpFactory = jumpFactory;

    factory = new LegacyFactory(factories, configuration);
    constructionQueue = new ArrayDeque<>();

    jumps = MultimapBuilder.hashKeys().hashSetValues().build();
    patientStates = new HashSet<>();
  }

  @Nullable
  @Override
  public EquivalenceClass add(EquivalenceClass initialClass) {
    if (initialClass.isFalse()) {
      return null;
    }

    EquivalenceClass state = factory.initialStateInternal(initialClass);
    constructionQueue.add(state);
    return state;
  }

  @Override
  public MutableAutomaton<EquivalenceClass, NoneAcceptance> build() {
    var automaton = new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory,
      constructionQueue, NoneAcceptance.INSTANCE, null, this::edgeTree);
    assert automaton.is(Automaton.Property.DETERMINISTIC);
    return MutableAutomatonFactory.copy(automaton);
  }

  private ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass state) {
    if (!jumps.containsKey(state)) {
      AnalysisResult<K> result = jumpFactory.analyse(state);
      jumps.putAll(state, result.jumps);

      if (result.type == TYPE.MAY) {
        patientStates.add(state);
      }
    }

    // Suppress edges, if the state is impatient (e.g. G a)
    if (!patientStates.contains(state)) {
      return ValuationTree.of();
    }

    var successors = factory.edgeTree(state);
    // There shouldn't be any rejecting sinks in the successor map.
    assert !successors.values().contains(Edge.of(factories.eqFactory.getFalse()));
    return successors;
  }

  Set<Jump<K>> getJumps(EquivalenceClass state) {
    return jumps.get(state);
  }
}
