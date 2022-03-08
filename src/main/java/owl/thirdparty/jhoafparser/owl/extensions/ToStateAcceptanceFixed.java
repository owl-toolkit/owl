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

/*
 * This file is from jhoafparser.
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

package owl.thirdparty.jhoafparser.owl.extensions;

import com.google.auto.value.AutoValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.thirdparty.jhoafparser.consumer.HOAConsumerException;
import owl.thirdparty.jhoafparser.storage.StoredAutomaton;
import owl.thirdparty.jhoafparser.storage.StoredAutomatonManipulator;
import owl.thirdparty.jhoafparser.storage.StoredEdgeImplicit;
import owl.thirdparty.jhoafparser.storage.StoredEdgeWithLabel;
import owl.thirdparty.jhoafparser.storage.StoredHeader;
import owl.thirdparty.jhoafparser.storage.StoredState;

/**
 * Convert automaton to state-based acceptance.
 *
 * <p> This conversion relies on storing the acceptance signatures of the
 * incoming transitions in the states, i.e., duplicating states depending
 * on the acceptance signature that was seen when reaching the state. </p>
 */
public class ToStateAcceptanceFixed implements StoredAutomatonManipulator {



  /** The source automaton. */
  private final StoredAutomaton source;
  /** The target automaton. */
  private final StoredAutomaton target;

  /** A unique table of bit set based acceptance signatures (for efficient storage). */
  private final Map<List<Integer>, List<Integer>> uniqueAcceptanceSignatures = new HashMap<>();
  /** Mapping the state+acc tuples to integers in the transformed automaton. */
  private final Map<StateWithAcceptance, Integer> transformedStates = new HashMap<>();
  /** Storage for the states that have yet to be explored. */
  private final NavigableMap<Integer, StateWithAcceptance> statesForOutput = new TreeMap<>();

  /** Default constructor. */
  public ToStateAcceptanceFixed() {
    source = null;
    target = null;
  }

  /** Constructor with source automaton. */
  private ToStateAcceptanceFixed(StoredAutomaton source) {
    this.source = source;
    target = new StoredAutomaton();
  }

  private static StoredHeader fixedStoredHeaderClone(StoredHeader header) {
    StoredHeader clone = header.clone();

    clone.getAcceptanceNames().clear();
    header.getAcceptanceNames().forEach(x -> clone.provideAcceptanceName(x.name(), x.extra()));

    clone.getMiscHeaders().clear();
    header.getMiscHeaders().forEach(x -> clone.addMiscHeader(x.name(), x.extra()));

    return clone;
  }

  /** Handle the transformation of the header. */
  private void handleHeader() {
    target.setStoredHeader(fixedStoredHeaderClone(source.getStoredHeader()));

    for (Iterator<String> it = target.getStoredHeader().getProperties().iterator(); it.hasNext();) {
      String property = it.next();
      switch (property) {
        case "trans-acc":
          it.remove();
          break;

        case "state-acc":
          break;

        // transition-structure properties, safe to pass on
        case "state-labels":
        case "trans-labels":
        case "implicit-labels":
        case "explicit-labels":
        case "univ-branch":
        case "no-univ-branch":
        case "deterministic":
        case "complete":

        // language-based properties, safe to pass on
        case "unambiguous":
        case "stutter-invariant":
        case "weak":
        case "very-weak":
        case "inherently-weak":
        case "terminal":
        case "tight":
          break;

        default:
          // unknown property, has to be removed
          it.remove();
          break;
      }
    }

    target.getStoredHeader().getProperties().add("state-acc");

    // don't know about non-Owl misc headers
    target.getStoredHeader().getMiscHeaders().removeIf(x -> !x.name().startsWith("owl"));
  }

  /** Handle the initial states, used as starting point for the later exploration phase. */
  private void handleStartStates() {
    target.getStoredHeader().getStartStates().clear();
    for (List<Integer> start : source.getStoredHeader().getStartStates()) {
      List<Integer> transformed = new ArrayList<>();

      for (Integer s : start) {
        // empty acceptance signature for the start states
        transformed.add(handleState(s, List.of()));
      }

      target.getStoredHeader().addStartStates(transformed);
    }
  }

  /**
   * Transform the transition structure.
   **/
  private void handleTransitionStructure() throws HOAConsumerException {
    // Do DFS of the reachable fragment...
    while (!statesForOutput.isEmpty()) {
      Entry<Integer, StateWithAcceptance> e = statesForOutput.firstEntry();
      statesForOutput.remove(e.getKey());
      int stateId = e.getKey();
      var sTransformed = Objects.requireNonNull(e.getValue());

      var storedState = source.getStoredState(sTransformed.originalStateId());
      var accSignatureState = storedState.getAccSignature();

      target.addState(
        new StoredState(
          stateId,
          storedState.getInfo(),
          storedState.getLabelExpr(),
          sTransformed.acceptanceSignature()));

      if (source.hasEdgesImplicit(sTransformed.originalStateId())) {
        if (source.hasEdgesWithLabel(sTransformed.originalStateId())) {
          throw new HOAConsumerException("Mixed explicit and implicit edges");
        }

        for (StoredEdgeImplicit edge : source.getEdgesImplicit(sTransformed.originalStateId())) {
          var conjSuccessors = transformSuccessors(
            edge.getConjSuccessors(), accSignatureState, edge.getAccSignature());
          target.addEdgeImplicit(stateId,
            new StoredEdgeImplicit(conjSuccessors, null));
        }
      } else if (source.hasEdgesWithLabel(sTransformed.originalStateId())) {
        for (StoredEdgeWithLabel edge : source.getEdgesWithLabel(sTransformed.originalStateId())) {
          var conjSuccessors = transformSuccessors(
            edge.getConjSuccessors(), accSignatureState, edge.getAccSignature());
          target.addEdgeWithLabel(stateId,
            new StoredEdgeWithLabel(edge.getLabelExpr(), conjSuccessors, null));
        }
      }
    }
  }

  /**
   * Transform the successors, using the acceptance signatures from the state and the edges.
   **/
  private List<Integer> transformSuccessors(List<Integer> conjSuccessors,
    @Nullable List<Integer> accSignatureState,
    @Nullable List<Integer> accSignatureEdge) {
    List<Integer> accSignature;

    if (accSignatureState == null || accSignatureEdge == null) {
      accSignature = accSignatureEdge == null ? accSignatureState : accSignatureEdge;
    } else {
      accSignature = new ArrayList<>(accSignatureState);
      accSignature.addAll(accSignatureEdge);
    }

    List<Integer> acceptanceSignature = handleAcceptanceSignature(accSignature);
    List<Integer> transformed = new ArrayList<>(conjSuccessors.size());

    for (int successor : conjSuccessors) {
      transformed.add(handleState(successor, acceptanceSignature));
    }

    return transformed;
  }

  /**
   * Try to find state with given stateID and acceptance signature. If it does not
   * already exists, create and mark for later exploration.
   **/
  private int handleState(int stateId, List<Integer> accSignature) {
    return transformedStates.computeIfAbsent(StateWithAcceptance.of(stateId, accSignature),
      sTransformed -> {
        int id = transformedStates.size();
        statesForOutput.put(id, sTransformed);
        return id;
      });
  }

  /** Acceptance signature: Translate from integer list representation representation to BitSet. */
  private List<Integer> handleAcceptanceSignature(@Nullable List<Integer> accSignature) {
    if (accSignature == null || accSignature.isEmpty()) {
      return List.of();
    }

    // remove duplicates...

    var copiedAccSignature = accSignature.toArray(Integer[]::new);
    Arrays.sort(copiedAccSignature);
    var sortedAccSignature = List.of(copiedAccSignature);
    return uniqueAcceptanceSignatures.computeIfAbsent(sortedAccSignature, Function.identity());
  }

  /** Checks whether the source automaton has any transition-based acceptance. */
  private boolean hasTransitionAcceptance() {
    int numStates = source.getNumberOfStates();

    for (int state = 0; state <= numStates; state++) {
      if (source.hasEdgesImplicit(state)) {
        for (StoredEdgeImplicit edge : source.getEdgesImplicit(state)) {
          if (edge.getAccSignature() != null && !edge.getAccSignature().isEmpty()) {
            return true;
          }
        }
      }

      if (source.hasEdgesWithLabel(state)) {
        for (StoredEdgeWithLabel edge : source.getEdgesWithLabel(state)) {
          if (edge.getAccSignature() != null && !edge.getAccSignature().isEmpty()) {
            return true;
          }
        }
      }
    }

    return false;
  }

  /** Transform the automaton, return the result. */
  private StoredAutomaton transform() throws HOAConsumerException {
    if (!hasTransitionAcceptance()) {
      source.getStoredHeader().getProperties().remove("trans-acc");
      source.getStoredHeader().getProperties().add("state-acc");
      return source;
    }

    handleHeader();
    handleStartStates();
    handleTransitionStructure();

    target.getStoredHeader().setNumberOfStates(target.getNumberOfStates());
    return target;
  }

  @Override
  public StoredAutomaton manipulate(StoredAutomaton automaton) throws HOAConsumerException {
    return new ToStateAcceptanceFixed(automaton).transform();
  }

  /**
   * A (state, acceptanceSingature) pair.
   */
  @AutoValue
  abstract static class StateWithAcceptance {

    /** The state id. */
    abstract int originalStateId();

    /** The acceptance signature. */
    abstract List<Integer> acceptanceSignature();

    static StateWithAcceptance of(int originalStateId, List<Integer> acceptanceSignature) {
      return new AutoValue_ToStateAcceptanceFixed_StateWithAcceptance(
        originalStateId, List.copyOf(acceptanceSignature));
    }
  }
}
