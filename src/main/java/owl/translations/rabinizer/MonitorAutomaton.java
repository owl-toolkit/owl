package owl.translations.rabinizer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edges;
import owl.automaton.edge.LabelledEdge;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetMapUtil;
import owl.factories.ValuationSetFactory;
import owl.ltl.GOperator;

class MonitorAutomaton implements Automaton<MonitorState, NoneAcceptance> {
  private final Automaton<MonitorState, ParityAcceptance> anyAutomaton;
  private final ImmutableMap<GSet, Automaton<MonitorState, ParityAcceptance>> automata;
  private final GSet base;
  private final GOperator formula;

  @SuppressWarnings("AssignmentToCollectionOrArrayFieldFromParameter")
  MonitorAutomaton(GOperator formula,
    Map<GSet, Automaton<MonitorState, ParityAcceptance>> automata) {
    this.automata = ImmutableMap.copyOf(automata);
    this.formula = formula;

    ImmutableSet.Builder<GOperator> baseBuilder = ImmutableSet.builder();
    for (GSet relevantSet : this.automata.keySet()) {
      baseBuilder.addAll(relevantSet);
    }
    this.base = new GSet(baseBuilder.build());

    anyAutomaton = automata.values().iterator().next();
  }

  @Override
  public void free() {
    anyAutomaton.free();
  }

  @Override
  public NoneAcceptance getAcceptance() {
    return NoneAcceptance.INSTANCE;
  }

  ImmutableMap<GSet, Automaton<MonitorState, ParityAcceptance>> getAutomata() {
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
  public Map<MonitorState, ValuationSet> getIncompleteStates() {
    assert anyAutomaton.getIncompleteStates().isEmpty();
    return ImmutableMap.of();
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
      ValuationSetMapUtil.add(successors, labelledEdge.edge.getSuccessor(),
        labelledEdge.valuations.copy());
    }
    Collection<LabelledEdge<MonitorState>> strippedEdges = new ArrayList<>(successors.size());
    successors.forEach((successor, valuation) ->
      strippedEdges.add(new LabelledEdge<>(Edges.create(successor), valuation)));
    return strippedEdges;
  }

  public GOperator getMonitoredG() {
    return formula;
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

  @Override
  public ImmutableList<String> getVariables() {
    return anyAutomaton.getVariables();
  }
}
