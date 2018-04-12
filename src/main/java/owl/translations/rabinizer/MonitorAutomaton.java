package owl.translations.rabinizer;

import com.google.common.collect.Collections2;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.GOperator;

class MonitorAutomaton implements Automaton<MonitorState, NoneAcceptance> {
  private final Automaton<MonitorState, ParityAcceptance> anyAutomaton;
  private final Map<GSet, Automaton<MonitorState, ParityAcceptance>> automata;
  private final GSet base;
  private final GOperator formula;

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
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
  public NoneAcceptance acceptance() {
    return NoneAcceptance.INSTANCE;
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
  public Collection<LabelledEdge<MonitorState>> labelledEdges(MonitorState state) {
    Collection<LabelledEdge<MonitorState>> labelledEdges = anyAutomaton.labelledEdges(state);
    Map<MonitorState, ValuationSet> successors = new HashMap<>(labelledEdges.size());

    for (LabelledEdge<MonitorState> labelledEdge : labelledEdges) {
      successors.merge(labelledEdge.edge.successor(), labelledEdge.valuations,
        ValuationSet::union);
    }

    return Collections2.transform(successors.entrySet(),
      x -> LabelledEdge.of(x.getKey(), x.getValue()));
  }

  @Nullable
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
