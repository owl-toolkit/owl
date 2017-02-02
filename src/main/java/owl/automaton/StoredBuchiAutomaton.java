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

package owl.automaton;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomLabel;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.factories.ValuationSetFactory;
import owl.factories.jdd.ValuationFactory;

// TODO Abstract a generic automaton reader class which just delegates the acceptance condition
// parsing
@SuppressWarnings("PMD.GodClass")
public class StoredBuchiAutomaton extends Automaton<StoredBuchiAutomaton.State, BuchiAcceptance> {
  private final Set<State> acceptingStates = new HashSet<>();

  StoredBuchiAutomaton(Factories factories) {
    super(new BuchiAcceptance(), factories);
  }

  private State addState() {
    State state = new State();

    // Add to transition table
    transitions.put(state, new HashMap<>());

    return state;
  }

  private void addTransition(State source, ValuationSet label, State successor) {
    Map<Edge<State>, ValuationSet> transition = transitions.get(source);
    Edge<State> edge;
    if (acceptingStates.contains(source)) {
      edge = Edges.create(successor, 0);
    } else {
      edge = Edges.create(successor);
    }
    ValuationSet oldLabel = transition.get(edge);

    if (oldLabel == null) {
      transition.put(edge, label);
    } else {
      oldLabel.addAll(label);
    }
  }

  public Map<StoredBuchiAutomaton.State, Map<Edge<StoredBuchiAutomaton.State>, ValuationSet>>
  getTransitions() {
    return transitions;
  }

  public boolean isAccepting(State state) {
    return acceptingStates.contains(state);
  }

  public static class Builder implements HOAConsumer {

    private final Deque<StoredBuchiAutomaton> automata = new ArrayDeque<>();
    @Nullable
    private StoredBuchiAutomaton automaton;
    private int implicitEdgeCounter;
    @Nullable
    private Integer initialState;
    @Nullable
    private State[] integerToState;
    private List<String> variables;
    @Nullable
    private ValuationSetFactory valuationSetFactory;

    private static boolean isAcceptingState(@Nullable List<Integer> list)
      throws HOAConsumerException {

      if (list == null || list.isEmpty()) {
        return false;
      }

      if (list.size() != 1) {
        throw new HOAConsumerException("Only state-based Büchi Acceptance is supported.");
      }

      Integer element = Iterables.getOnlyElement(list);

      if (element != 0) {
        throw new HOAConsumerException(
          "Only state-based Büchi Acceptance is supported. Malformed index: " + element);
      }

      return true;
    }

    @Override
    public void addAlias(String s, BooleanExpression<AtomLabel> booleanExpression)
      throws HOAConsumerException {
      throw new HOAConsumerException("Unsupported Operation.");
    }

    @Override
    public void addEdgeImplicit(int i, List<Integer> list, List<Integer> list1)
      throws HOAConsumerException {
      assert valuationSetFactory != null;
      addEdgeWithLabel(i,
        BooleanExpression.fromImplicit(implicitEdgeCounter, valuationSetFactory.getSize()), list,
        list1);
      implicitEdgeCounter++;
    }

    @Override
    public void addEdgeWithLabel(int i, BooleanExpression<AtomLabel> booleanExpression,
      List<Integer> successors, List<Integer> accList) throws HOAConsumerException {
      assert integerToState != null;
      assert automaton != null;

      if (accList != null && !accList.isEmpty()) {
        throw new HOAConsumerException("Edge acceptance is not supported.");
      }

      if (successors == null || successors.isEmpty()) {
        return;
      }

      if (successors.size() > 1) {
        throw new HOAConsumerException("Universal or create transitions are not supported.");
      }

      int index = Iterables.getOnlyElement(successors);
      State successor = integerToState[index];

      if (successor == null) {
        successor = automaton.addState();
        integerToState[index] = successor;
      }

      State source = integerToState[i];
      automaton.addTransition(source, toValuationSet(booleanExpression), successor);
    }

    @Override
    public void addMiscHeader(String s, List<Object> list) throws HOAConsumerException {
      // No operation
    }

    @Override
    public void addProperties(List<String> list) throws HOAConsumerException {
      // No operation
    }

    @Override
    public void addStartStates(List<Integer> list) throws HOAConsumerException {
      if (list.size() != 1 || initialState != null) {
        throw new HOAConsumerException("Only a single initial state is supported.");
      }

      initialState = list.get(0);
    }

    @Override
    public void addState(int i, String s, BooleanExpression<AtomLabel> booleanExpression,
      @Nullable List<Integer> list) throws HOAConsumerException {
      assert automaton != null;
      ensureSpaceInMap(i);
      assert integerToState != null;

      String label;
      if (s == null) {
        label = Integer.toString(i);
      } else {
        label = s;
      }
      State state = integerToState[i];

      // Create state, if missing.
      if (state == null) {
        state = automaton.addState();
        integerToState[i] = state;
      }

      // Update label and acceptance marking for state.
      state.setLabel(label);

      if (isAcceptingState(list)) {
        automaton.acceptingStates.add(state);
      }
    }

    private void ensureSpaceInMap(int id) {
      if (integerToState == null) {
        integerToState = new State[id + 1];
      }

      if (id >= integerToState.length) {
        integerToState = Arrays.copyOf(integerToState, id + 1);
      }
    }

    public Iterable<StoredBuchiAutomaton> getAutomata() {
      return automata;
    }

    @Override
    public void notifyAbort() {
      notifyHeaderStart(null);
    }

    @Override
    public void notifyBodyStart() throws HOAConsumerException {
      if (initialState == null) {
        throw new HOAConsumerException("No initial state");
      }
      if (valuationSetFactory == null) {
        valuationSetFactory = new ValuationFactory(0);
      }

      automaton = new StoredBuchiAutomaton(Registry.getFactories(valuationSetFactory.getSize()));
      ensureSpaceInMap(initialState);
      assert integerToState != null;
      integerToState[initialState] = automaton.addState();
      automaton.setInitialState(integerToState[initialState]);
      automaton.setVariables(variables);
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
      automata.add(automaton);
      notifyHeaderStart(null);
    }

    @Override
    public void notifyEndOfState(int i) throws HOAConsumerException {
      implicitEdgeCounter = 0;
    }

    @Override
    // We reset the state of the reader here
    @SuppressWarnings("PMD.NullAssignment")
    public void notifyHeaderStart(String s) {
      valuationSetFactory = null;
      integerToState = null;
      initialState = null;
      automaton = null;
    }

    @Override
    public void notifyWarning(String s) throws HOAConsumerException {
      // No operation
    }

    @Override
    public boolean parserResolvesAliases() {
      return false;
    }

    @Override
    public void provideAcceptanceName(String s, List<Object> list) throws HOAConsumerException {
      if (!"Buchi".equals(s)) {
        throw new HOAConsumerException("Unsupported Acceptance: " + s);
      }
    }

    @Override
    public void setAPs(List<String> list) throws HOAConsumerException {
      this.variables = ImmutableList.copyOf(list);
      valuationSetFactory = new ValuationFactory(list.size());
    }

    @Override
    public void setAcceptanceCondition(int i, BooleanExpression<AtomAcceptance> booleanExpression)
      throws HOAConsumerException {
      if (i == 1 && booleanExpression.isAtom()) {
        return;
      }

      throw new HOAConsumerException(
        "Unsupported Acceptance Conditions: " + i + ' ' + booleanExpression);
    }

    @Override
    public void setName(String s) throws HOAConsumerException {
      // No operation
    }

    @Override
    public void setNumberOfStates(int i) throws HOAConsumerException {
      integerToState = new State[i];
    }

    @Override
    public void setTool(String s, String s1) throws HOAConsumerException {
      // No operation
    }

    private ValuationSet toValuationSet(BooleanExpression<AtomLabel> label) {
      if (label.isFALSE()) {
        return valuationSetFactory.createEmptyValuationSet();
      }

      if (label.isTRUE()) {
        return valuationSetFactory.createUniverseValuationSet();
      }

      if (label.isAtom()) {
        BitSet bs = new BitSet();
        bs.set(label.getAtom().getAPIndex());
        return valuationSetFactory.createValuationSet(bs, bs);
      }

      if (label.isNOT()) {
        return toValuationSet(label.getLeft()).complement();
      }

      if (label.isAND()) {
        ValuationSet valuationSet = toValuationSet(label.getLeft());
        valuationSet.retainAll(toValuationSet(label.getRight()));
        return valuationSet;
      }

      if (label.isOR()) {
        ValuationSet valuationSet = toValuationSet(label.getLeft());
        valuationSet.addAll(toValuationSet(label.getRight()));
        return valuationSet;
      }

      throw new IllegalArgumentException("Unsupported Case: " + label);
    }
  }

  public static class State implements AutomatonState<State> {
    @Nullable
    private String label;

    @Nullable
    public String getLabel() {
      return label;
    }

    @Nonnull
    @Override
    public BitSet getSensitiveAlphabet() {
      throw new UnsupportedOperationException(
        "Stored Automaton State cannot perform on-demand computations.");
    }

    @Nullable
    @Override
    public Edge<State> getSuccessor(@Nonnull BitSet valuation) {
      throw new UnsupportedOperationException(
        "Stored Automaton State cannot perform on-demand computations.");
    }

    public void setLabel(String label) {
      this.label = label;
    }

    @Override
    public String toString() {
      if (label == null) {
        return "null";
      }
      return label;
    }
  }
}
