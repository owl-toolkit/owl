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
  public NoneAcceptance getAcceptance() {
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
  public ValuationSetFactory getFactory() {
    return anyAutomaton.getFactory();
  }

  @Override
  public Set<MonitorState> getInitialStates() {
    return anyAutomaton.getInitialStates();
  }

  @Override
  public Collection<LabelledEdge<MonitorState>> getLabelledEdges(MonitorState state) {
    Collection<LabelledEdge<MonitorState>> labelledEdges = anyAutomaton.getLabelledEdges(state);
    Map<MonitorState, ValuationSet> successors = new HashMap<>(labelledEdges.size());

    for (LabelledEdge<MonitorState> labelledEdge : labelledEdges) {
      successors.merge(labelledEdge.edge.getSuccessor(), labelledEdge.valuations,
        ValuationSet::union);
    }

    return Collections2.transform(successors.entrySet(),
      x -> LabelledEdge.of(x.getKey(), x.getValue()));
  }

  @Nullable
  @Override
  public String getName() {
    return "Monitor for " + formula + " with base " + base;
  }

  @Override
  public Set<MonitorState> getStates() {
    return anyAutomaton.getStates();
  }

  @Nullable
  @Override
  public MonitorState getSuccessor(MonitorState state, BitSet valuation) {
    return anyAutomaton.getSuccessor(state, valuation);
  }

  @Override
  public Set<MonitorState> getSuccessors(MonitorState state) {
    return anyAutomaton.getSuccessors(state);
  }
}
