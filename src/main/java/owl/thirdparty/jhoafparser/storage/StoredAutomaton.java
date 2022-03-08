//==============================================================================
//
//	Copyright (c) 2014-
//	Authors:
//	* Joachim Klein <klein@tcs.inf.tu-dresden.de>
//	* David Mueller <david.mueller@tcs.inf.tu-dresden.de>
//
//------------------------------------------------------------------------------
//
//	This file is part of the jhoafparser library, http://automata.tools/hoa/jhoafparser/
//
//	The jhoafparser library is free software; you can redistribute it and/or
//	modify it under the terms of the GNU Lesser General Public
//	License as published by the Free Software Foundation; either
//	version 2.1 of the License, or (at your option) any later version.
//
//	The jhoafparser library is distributed in the hope that it will be useful,
//	but WITHOUT ANY WARRANTY; without even the implied warranty of
//	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
//	Lesser General Public License for more details.
//
//	You should have received a copy of the GNU Lesser General Public
//	License along with this library; if not, write to the Free Software
//	Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
//
//==============================================================================

package owl.thirdparty.jhoafparser.storage;

import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;
import owl.logic.propositional.PropositionalFormula;
import owl.thirdparty.jhoafparser.ast.AtomLabel;
import owl.thirdparty.jhoafparser.consumer.HOAConsumer;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;

/**
 * Stores information about a HOA automaton, both the transition structure
 * and header information.
 * <p>
 * A stored automaton can be "fed back" to a consumer via the {@code feedToConsumer} method.
 */
public class StoredAutomaton
{
	/** A unique table for acceptance signatures */
	private Interner<List<Integer>> acceptanceSignatures;
	/** A unique table for label expressions */
	private Interner<PropositionalFormula<AtomLabel>> labelExpressions;

	/** A unique table for implicit edges */
	private Interner<StoredEdgeImplicit> edgesImplicit;
	/** A unique table for edges with labels */
	private Interner<StoredEdgeWithLabel> edgesWithLabel;

	/** stores (for each state) the list of implicit edges */
	private TreeMap<Integer, ArrayList<StoredEdgeImplicit>> stateToImplicitEdges;

	/** stores (for each state) the list of edges with labels */
	private TreeMap<Integer, ArrayList<StoredEdgeWithLabel>> stateToEdgesWithLabel;

	/** map state index -> state */
	private TreeMap<Integer, StoredState> indexToState;

	/** The stored header information */
	private StoredHeader storedHeader;

	/** Constructor, empty automaton */
	public StoredAutomaton()
	{
		edgesImplicit = Interners.newStrongInterner();
		edgesWithLabel = Interners.newStrongInterner();
		acceptanceSignatures = Interners.newStrongInterner();
		labelExpressions = Interners.newStrongInterner();

		stateToImplicitEdges = new TreeMap<>();
		stateToEdgesWithLabel = new TreeMap<>();
		indexToState = new TreeMap<>();

		storedHeader = new StoredHeader();
	}

	/** Add a state. The state index has to be unique, otherwise UnsupportedOperationException is thrown */
	public void addState(StoredState state) throws UnsupportedOperationException {
		Integer stateId = state.getStateId();
		if (indexToState.containsKey(stateId)) {
			throw new UnsupportedOperationException("Duplicate state (id = "+stateId+")");
		}
		indexToState.put(stateId, state);
	}

	/**
	 * Add an implicit edge for a state.
	 * <p>
	 * The edge is added as the edge with the next implicit index,
	 * i.e., such edges have to be added in order.
	 */
	public void addEdgeImplicit(int stateId, StoredEdgeImplicit edge) {
		edge = edgesImplicit.intern(edge);

    ArrayList<StoredEdgeImplicit> edges = stateToImplicitEdges.computeIfAbsent(stateId,
      k -> new ArrayList<>());

    edges.add(edge);
	}

	/** Add an edge with a label expression for a state */
	public void addEdgeWithLabel(int stateId, StoredEdgeWithLabel edge) {
		edge = edgesWithLabel.intern(edge);

    ArrayList<StoredEdgeWithLabel> edges = stateToEdgesWithLabel.computeIfAbsent(stateId,
      k -> new ArrayList<>());

    edges.add(edge);
	}

	/** Remove all edges with label expression for the given state */
	public void clearEdgesWithLabel(int stateId) {
		stateToEdgesWithLabel.remove(stateId);
	}

	/** Remove all edges with implicit edge labels for the given state */
	public void clearEdgesImplicit(int stateId) {
		stateToImplicitEdges.remove(stateId);
	}

	/** Set the stored header */
	public void setStoredHeader(StoredHeader header) {
		this.storedHeader = header;
	}

	/** Get the stored header */
	public StoredHeader getStoredHeader() {
		return storedHeader;
	}

	/** Get stored state with the given id */
	public StoredState getStoredState(int stateId) {
		return indexToState.get(stateId);
	}

	/**
	 * Get an iterable over the stored states,
	 * ordered by the state indizes
	 **/
	public Iterable<StoredState> getStoredStates() {
		return indexToState.values();
	}

	/** Get the number of stored states */
	public int getNumberOfStates() {
		return indexToState.size();
	}

	/** Get the highest index of the stored states */
	public int getHighestStateIndex() {
		return indexToState.lastKey();
	}

	/**
	 * Get an iterable over the implicit edges of the state with the given id,
	 * in the order of the implicit indizes
	 **/
	public Iterable<StoredEdgeImplicit> getEdgesImplicit(int stateId) {
		return stateToImplicitEdges.get(stateId);
	}

	/**
	 * Get an iterable over the edges with labels of the state with the given id,
	 * in insertion order
	 **/
	public Iterable<StoredEdgeWithLabel> getEdgesWithLabel(int stateId) {
		return stateToEdgesWithLabel.get(stateId);
	}

	/** Returns true if the state with the given id exists */
	public boolean hasState(int stateId) {
		return indexToState.containsKey(stateId);
	}

	/** Returns true if the state with the given id has stored implicit edges */
	public boolean hasEdgesImplicit(int stateId) {
		return stateToImplicitEdges.containsKey(stateId);
	}

	/** Returns true if the state with the given id has stored edges with labels */
	public boolean hasEdgesWithLabel(int stateId) {
		return stateToEdgesWithLabel.containsKey(stateId);
	}

	/**
	 * Stores the label expression such that equal expressions are identified:
	 * If there is already an expression equal to the argument stored, return that,
	 * otherwise store and return the argument.
	 */
	public PropositionalFormula<AtomLabel> findOrAdd(PropositionalFormula<AtomLabel> exprLabel) {
    return exprLabel == null ? null : labelExpressions.intern(exprLabel);
  }

	/**
	 * Stores the acceptance signature such that equal signatures are identified:
	 * If there is already a signature equal to the argument stored, return that,
	 * otherwise store and return the argument.
	 */
	public List<Integer> findOrAdd(Collection<Integer> acceptanceSignature) {
    return acceptanceSignature == null
      ? null
      : acceptanceSignatures.intern(List.copyOf(acceptanceSignature));
  }

	/** Feed the stored automaton to the consumer */
	public void feedToConsumer(HOAConsumer c) throws HOAConsumerException {
		c.notifyHeaderStart("v1");
		storedHeader.feedToConsumer(c);
		c.notifyBodyStart();
		feedTransitionStructureToConsumer(c);
		c.notifyEnd();
	}

	/**
	 * Static helper: feed the given edge list to the consumer.
	 **/
	public static void feedEdgesImplicitToConsumer(HOAConsumer consumer, int stateId, List<StoredEdgeImplicit> edges) throws HOAConsumerException {
		for (StoredEdgeImplicit edge : edges) {
			consumer.addEdgeImplicit(stateId, edge.getConjSuccessors(), edge.getAccSignature());
		}
	}

	/**
	 * Static helper: feed the given edge list to the consumer.
	 **/
	public static void feedEdgesWithLabelToConsumer(HOAConsumer consumer, int stateId, List<StoredEdgeWithLabel> edges) throws HOAConsumerException {
		for (StoredEdgeWithLabel edge : edges) {
			consumer.addEdgeWithLabel(stateId, edge.getLabelExpr(), edge.getConjSuccessors(), edge.getAccSignature());
		}
	}

	/**
	 * Static helper: feed the given edge lists to the consumer.<br>
	 * Note: only one of {@code edgesImplicit} and {@code edgesWithLabel} may be non-{@code null}.
	 **/
	public static void feedEdgesToConsumer(HOAConsumer consumer, int stateId, List<StoredEdgeImplicit> edgesImplicit, List<StoredEdgeWithLabel> edgesWithLabel) throws HOAConsumerException {
		if (edgesImplicit != null) {
			if (edgesWithLabel != null) {
				throw new HOAConsumerException("Both explicit and implicit edges for state "+stateId);
			}
			feedEdgesImplicitToConsumer(consumer, stateId, edgesImplicit);
		} else if (edgesWithLabel != null) {
			feedEdgesWithLabelToConsumer(consumer, stateId, edgesWithLabel);
		} else {
			// no edges
		}
	}

	/**
	 * Feed the transition structure (addState, addEdgeImplicit, addEdgeWithLabel, notifyEndOfState)
	 * for all the states / edges of the stored automaton to the consumer
	 **/
	public void feedTransitionStructureToConsumer(HOAConsumer consumer) throws HOAConsumerException {
		for (StoredState s : indexToState.values()) {
			consumer.addState(s.getStateId(), s.getInfo(), s.getLabelExpr(), s.getAccSignature());

			feedEdgesToConsumer(consumer, s.getStateId(), stateToImplicitEdges.get(s.getStateId()), stateToEdgesWithLabel.get(s.getStateId()));

			consumer.notifyEndOfState(s.getStateId());
		}
	}

	/** Returns true if the stored automaton has universal branching */
	public boolean hasUniversalBranching()
	{
		if (storedHeader != null) {
			for (List<Integer> start : storedHeader.getStartStates()) {
				if (start.size() != 1) {
					return true;
				}
			}
		}

		for (ArrayList<StoredEdgeImplicit> edgesList : stateToImplicitEdges.values()) {
			for (StoredEdgeImplicit edges : edgesList) {
				if (edges.getConjSuccessors().size() != 1) {
					return true;
				}
			}
		}

		for (ArrayList<StoredEdgeWithLabel> edgesList : stateToEdgesWithLabel.values()) {
			for (StoredEdgeWithLabel edges : edgesList) {
				if (edges.getConjSuccessors().size() != 1) {
					return true;
				}
			}
		}

		return false;
	}

	/** Returns true if the stored automaton has edges with implicit labels. */
	public boolean hasEdgesImplicit()
	{
		for (ArrayList<StoredEdgeImplicit> edgeList : stateToImplicitEdges.values()) {
			if (edgeList.size() > 0) return true;
		}
		return false;
	}

	/** Returns true if the stored automaton has edges with explicit labels. */
	public boolean hasEdgesWithLabel()
	{
		for (ArrayList<StoredEdgeWithLabel> edgeList : stateToEdgesWithLabel.values()) {
			if (edgeList.size() > 0) return true;
		}
		return false;
	}

}
