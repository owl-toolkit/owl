/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.rabinizer;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.EdgeMapAutomatonMixin;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.GOperator;

class MonitorAutomaton implements EdgeMapAutomatonMixin<MonitorState, AllAcceptance> {
  private final Automaton<MonitorState, ParityAcceptance> anyAutomaton;
  private final Map<GSet, Automaton<MonitorState, ParityAcceptance>> automata;
  private final GSet base;
  private final GOperator formula;

  MonitorAutomaton(GOperator formula,
    Map<GSet, Automaton<MonitorState, ParityAcceptance>> automata) {
    this.automata = Map.copyOf(automata);
    this.formula = formula;

    Set<GOperator> baseBuilder = new HashSet<>();
    for (GSet relevantSet : this.automata.keySet()) {
      baseBuilder.addAll(relevantSet);
    }
    this.base = new GSet(baseBuilder);

    anyAutomaton = automata.values().iterator().next();
  }

  @Override
  public AllAcceptance acceptance() {
    return AllAcceptance.INSTANCE;
  }

  Map<GSet, Automaton<MonitorState, ParityAcceptance>> getAutomata() {
    return automata;
  }

  public Automaton<MonitorState, ParityAcceptance> getAutomaton(GSet gSet) {
    GSet intersection = base.intersection(gSet);
    Automaton<MonitorState, ParityAcceptance> result = automata.get(intersection);
    assert result != null :
      String.format("No automaton found for gSet %s with base %s", gSet, base);
    return result;
  }

  @Override
  public ValuationSetFactory factory() {
    return anyAutomaton.factory();
  }

  @Override
  public Set<MonitorState> initialStates() {
    return anyAutomaton.initialStates();
  }

  @Override
  public Map<Edge<MonitorState>, ValuationSet> edgeMap(MonitorState state) {
    return Collections3.transformMap(anyAutomaton.edgeMap(state), Edge::withoutAcceptance);
  }

  @Override
  public String name() {
    return "Monitor for " + formula + " with base " + base;
  }

  @Override
  public Set<MonitorState> states() {
    return anyAutomaton.states();
  }

  @Nullable
  @Override
  public MonitorState successor(MonitorState state, BitSet valuation) {
    return anyAutomaton.successor(state, valuation);
  }

  @Override
  public Set<MonitorState> successors(MonitorState state) {
    return anyAutomaton.successors(state);
  }
}
