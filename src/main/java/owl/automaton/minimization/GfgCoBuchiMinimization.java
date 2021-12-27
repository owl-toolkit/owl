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

package owl.automaton.minimization;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.EmptyAutomaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.algorithm.LanguageContainment;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaWriter;
import owl.bdd.MtBdd;
import owl.collections.Collections3;

/**
 * This class implements [ICALP'19] minimization of GFG automata.
 */
public final class GfgCoBuchiMinimization {

  private GfgCoBuchiMinimization() {}

  public static <S> Automaton<Set<S>, CoBuchiAcceptance> minimize(
    Automaton<S, ? extends CoBuchiAcceptance> dcw) {

    Preconditions.checkArgument(dcw.is(Automaton.Property.DETERMINISTIC));

    var dcwCopy = HashMapAutomaton.copyOf(dcw);
    var safeComponents = safeComponents(dcwCopy);

    if (dcwCopy.initialStates().isEmpty()) {
      return EmptyAutomaton.of(dcw.atomicPropositions(), CoBuchiAcceptance.INSTANCE);
    }

    normalize(dcwCopy, safeComponents);
    assert LanguageContainment.equalsCoBuchi(dcw, dcwCopy);

    var safeCentralized = HashMapAutomaton.copyOf(safeCentralize(dcwCopy, safeComponents));
    assert LanguageContainment.equalsCoBuchi(dcwCopy, safeCentralized);

    var safeMinimized = HashMapAutomaton.copyOf(safeMinimize(safeCentralized));
    assert LanguageContainment.equalsCoBuchi(safeCentralized, safeMinimized)
      : "before: " + HoaWriter.toString(safeCentralized)
       + "\nafter: " + HoaWriter.toString(safeMinimized);

    return safeMinimized;
  }

  private static <S> List<Set<S>> safeComponents(Automaton<S, ? extends CoBuchiAcceptance> ncw) {
    return SccDecomposition.of(
      ncw.states(),
      state -> ncw.edges(state).stream()
        .filter(edge -> !edge.colours().contains(0))
        .map(Edge::successor)
        .collect(Collectors.toSet()))
      .sccs();
  }

  private static <S> void normalize(MutableAutomaton<S, ? extends CoBuchiAcceptance> ncw,
    List<Set<S>> safeComponents) {

    ncw.updateEdges((state, edge) -> {
      if (componentId(state, safeComponents) == componentId(edge.successor(), safeComponents)) {
        return edge;
      } else {
        return edge.withAcceptance(0);
      }
    });

    ncw.trim();
  }

  private static <S> boolean languageEquivalent(
    Automaton<S, ? extends CoBuchiAcceptance> ncw, S q, S p) {

    return LanguageContainment.equalsCoBuchi(
      Views.replaceInitialStates(ncw, Set.of(q)),
      Views.replaceInitialStates(ncw, Set.of(p)));
  }

  private static <S> Automaton<S, AllAcceptance>
    safeView(Automaton<S, ? extends CoBuchiAcceptance> ncw, S q) {

    return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
      ncw.atomicPropositions(), ncw.factory(), Set.of(q), AllAcceptance.INSTANCE) {

      @Override
      public MtBdd<Edge<S>> edgeTreeImpl(S state) {
        return ncw.edgeTree(state).map(edges -> {
          var edgesCopy = new HashSet<>(edges);
          edgesCopy.removeIf(sEdge -> !sEdge.colours().isEmpty());
          return edgesCopy;
        });
      }
    };
  }

  private static <S> boolean subsafeEquivalent(
    Automaton<S, ? extends CoBuchiAcceptance> ncw, S q, S p) {

    return languageEquivalent(ncw, q, p)
      && LanguageContainment.containsAll(safeView(ncw, q), safeView(ncw, p));
  }

  private static <S> boolean stronglyEquivalent(
    Automaton<S, ? extends CoBuchiAcceptance> ncw, S q, S p) {

    return languageEquivalent(ncw, q, p)
      && LanguageContainment.equalsAll(safeView(ncw, q), safeView(ncw, p));
  }

  private static <S> Automaton<S, CoBuchiAcceptance> safeCentralize(
    Automaton<S, ? extends CoBuchiAcceptance> ncw, List<Set<S>> safeComponents) {

    var frontier = Collections3.maximalElements(safeComponents, (component1, component2) -> {
      S representative1 = component1.iterator().next();

      for (S representative2 : component2) {
        if (subsafeEquivalent(ncw, representative1, representative2)) {
          return true;
        }
      }

      return false;
    });

    S initialState = null;

    for (Set<S> safeComponent : frontier) {
      if (safeComponent.contains(ncw.initialState())) {
        initialState = ncw.initialState();
        break;
      }
    }

    if (initialState == null) {
      outer:
      for (Set<S> safeComponent : frontier) {
        for (S state : safeComponent) {
          if (subsafeEquivalent(ncw, ncw.initialState(), state)) {
            initialState = state;
            break outer;
          }
        }
      }
    }

    assert initialState != null;

    return new AbstractMemoizingAutomaton.EdgesImplementation<>(
      ncw.atomicPropositions(), ncw.factory(), Set.of(initialState), CoBuchiAcceptance.INSTANCE) {

      @Override
      protected Set<Edge<S>> edgesImpl(S state, BitSet valuation) {
        Set<Edge<S>> acceptingEdges = new HashSet<>();
        Set<S> rejectingSuccessors = new HashSet<>();

        ncw.edges(state, valuation).forEach(edge -> {
          if (edge.colours().contains(0)) {
            rejectingSuccessors.add(edge.successor());
          } else {
            assert edge.colours().isEmpty();
            acceptingEdges.add(edge);
          }
        });

        if (acceptingEdges.isEmpty()) {
          Set<Edge<S>> rejectingEdges = new HashSet<>();

          for (S rejectingSuccessor : rejectingSuccessors) {
            for (Set<S> safeComponent : frontier) {
              for (S safeSuccessor : safeComponent) {
                if (subsafeEquivalent(ncw, rejectingSuccessor, safeSuccessor)) {
                  rejectingEdges.add(Edge.of(safeSuccessor, 0));
                }
              }
            }
          }

          return rejectingEdges;
        } else {
          return acceptingEdges;
        }
      }
    };
  }

  private static <S> Automaton<Set<S>, CoBuchiAcceptance>
    safeMinimize(Automaton<S, ? extends CoBuchiAcceptance> ncw) {

    List<Set<S>> equivalenceClasses = new ArrayList<>();

    outer:
    for (S state : ncw.states()) {
      // Scan for existing class
      for (Set<S> equivalenceClass : equivalenceClasses) {
        var representative = equivalenceClass.iterator().next();

        if (stronglyEquivalent(ncw, state, representative)) {
          equivalenceClass.add(state);
          continue outer;
        }
      }

      // Add new equivalence class.
      equivalenceClasses.add(new HashSet<>(Set.of(state)));
    }

    List<Set<S>> immutableEquivalenceClass = equivalenceClasses.stream()
      .map(Set::copyOf)
      .toList();

    Set<Set<S>> initialStates = immutableEquivalenceClass.stream()
      .filter(x -> !Collections.disjoint(x, ncw.initialStates()))
      .collect(Collectors.toUnmodifiableSet());

    return new AbstractMemoizingAutomaton.EdgesImplementation<>(
      ncw.atomicPropositions(),
      ncw.factory(),
      initialStates,
      CoBuchiAcceptance.INSTANCE) {

      @Override
      protected Set<Edge<Set<S>>> edgesImpl(Set<S> state, BitSet valuation) {
        var edges = new HashSet<Edge<Set<S>>>();

        for (S representative : state) {
          var type = EdgeType.UNKNOWN;

          for (Edge<S> edge : ncw.edges(representative, valuation)) {
            var successorRepresentative = edge.successor();

            if (edge.colours().contains(0)) {
              assert type == EdgeType.UNKNOWN || type == EdgeType.REJECTING;
              type = EdgeType.REJECTING;
            } else {
              assert type == EdgeType.UNKNOWN || type == EdgeType.ACCEPTING;
              type = EdgeType.ACCEPTING;
            }

            var successor
              = findEquivalenceClass(immutableEquivalenceClass, successorRepresentative);
            edges.add(type == EdgeType.REJECTING
              ? Edge.of(successor, 0)
              : Edge.of(successor));
          }
        }

        return edges;
      }
    };
  }

  enum EdgeType {
    ACCEPTING, REJECTING, UNKNOWN
  }

  private static <S> int componentId(S state, List<Set<S>> components) {
    int i = 0;

    for (Set<S> component : components) {
      if (component.contains(state)) {
        return i;
      }

      i++;
    }

    throw new IllegalStateException("Internal error.");
  }

  private static <S> Set<S> findEquivalenceClass(
    List<Set<S>> equivalenceClasses, S representative) {

    for (Set<S> equivalenceClass : equivalenceClasses) {
      if (equivalenceClass.contains(representative)) {
        return equivalenceClass;
      }
    }

    throw new IllegalStateException("Internal error.");
  }
}
