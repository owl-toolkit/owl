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

import static com.google.common.base.Preconditions.checkArgument;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import jhoafparser.consumer.HOAConsumerPrint;
import owl.automaton.Automaton.LabelledEdgeVisitor;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.automaton.output.HoaPrintable;
import owl.automaton.output.HoaPrintable.HoaOption;
import owl.collections.ValuationSet;

public final class AutomatonUtil {

  private AutomatonUtil() {}

  public static <A extends OmegaAcceptance> Automaton<Object, A> cast(Object automaton,
    Class<A> acceptanceClass) {
    return cast(automaton, Object.class, acceptanceClass);
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Object automaton,
    Class<S> stateClass, Class<A> acceptanceClass) {
    checkArgument(automaton instanceof Automaton, "Expected automaton, got %s",
      automaton.getClass().getName());
    Automaton<?, ?> castedAutomaton = (Automaton<?, ?>) automaton;

    checkAcceptanceClass(castedAutomaton, acceptanceClass);
    // Very costly to check, so only asserted
    assert checkStateClass(castedAutomaton, stateClass);
    return (Automaton<S, A>) castedAutomaton;
  }

  @SuppressWarnings("unchecked")
  public static <S, A extends OmegaAcceptance> Automaton<S, A> cast(Automaton<S, ?> automaton,
    Class<A> acceptanceClass) {
    checkAcceptanceClass(automaton, acceptanceClass);
    return (Automaton<S, A>) automaton;
  }

  private static <S> boolean checkAcceptanceClass(Automaton<S, ?> automaton, Class<?> clazz) {
    checkArgument(clazz.isInstance(automaton.acceptance()),
      "Expected acceptance type %s, got %s", clazz.getName(), automaton.acceptance().getClass());
    return true;
  }

  private static <S> boolean checkStateClass(Automaton<S, ?> automaton, Class<?> clazz) {
    if (Object.class.equals(clazz)) {
      return true;
    }

    for (Object state : automaton.states()) {
      checkArgument(clazz.isInstance(state),
        "Expected states of type %s but got %s.", clazz.getName(), state.getClass().getName());
    }

    return true;
  }

  public static <S> void forEachNonTransientEdge(Automaton<S, ?> automaton,
    BiConsumer<S, Edge<S>> action) {
    List<Set<S>> sccs = SccDecomposition.computeSccs(automaton, false);

    for (Set<S> scc : sccs) {
      for (S state : scc) {
        automaton.forEachEdge(state, edge -> {
          if (scc.contains(edge.successor())) {
            action.accept(state, edge);
          }
        });
      }
    }
  }

  /**
   * Determines all states which are incomplete, i.e. there are valuations for which the
   * state has no successor.
   *
   * @param automaton
   *     The automaton.
   *
   * @return The set of incomplete states and the missing valuations.
   */
  public static <S> Map<S, ValuationSet> getIncompleteStates(Automaton<S, ?> automaton) {
    Map<S, ValuationSet> incompleteStates = new HashMap<>();

    LabelledEdgeVisitor<S> visitor = new LabelledEdgeVisitor<>() {
      private final ValuationSet emptyValuationSet = automaton.factory().empty();
      private ValuationSet valuationSet = emptyValuationSet;

      @Override
      public void visitLabelledEdge(Edge<S> edge, ValuationSet valuationSet) {
        this.valuationSet = this.valuationSet.union(valuationSet);
      }

      @Override
      public void enter(S state) {
        this.valuationSet = emptyValuationSet;
      }

      @Override
      public void exit(S state) {
        if (!valuationSet.isUniverse()) {
          incompleteStates.put(state, valuationSet.complement());
        }

        this.valuationSet = emptyValuationSet;
      }
    };

    automaton.accept(visitor);
    return incompleteStates;
  }

  public static <S> Set<S> getNondeterministicStates(Automaton<S, ?> automaton) {
    Set<S> nondeterministicStates = new HashSet<>();

    LabelledEdgeVisitor<S> visitor = new LabelledEdgeVisitor<>() {
      private final ValuationSet emptyValuationSet = automaton.factory().empty();
      private ValuationSet valuationSet = emptyValuationSet;
      private boolean nondeterministicState = false;

      @Override
      public void visitLabelledEdge(Edge<S> edge, ValuationSet valuationSet) {
        if (nondeterministicState || this.valuationSet.intersects(valuationSet)) {
          nondeterministicState = true;
        } else {
          this.valuationSet = valuationSet.union(valuationSet);
        }
      }

      @Override
      public void enter(S state) {
        this.nondeterministicState = false;
        this.valuationSet = emptyValuationSet;
      }

      @Override
      public void exit(S state) {
        if (nondeterministicState) {
          nondeterministicStates.add(state);
        }
      }
    };

    automaton.accept(visitor);
    return nondeterministicStates;
  }

  public static String toHoa(HoaPrintable printable) {
    ByteArrayOutputStream writer = new ByteArrayOutputStream();
    HOAConsumerPrint hoa = new HOAConsumerPrint(writer);
    printable.toHoa(hoa, EnumSet.of(HoaOption.ANNOTATIONS));
    return new String(writer.toByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * Collect all acceptance sets occurring on transitions within the given state set.
   *
   * @param automaton the automaton
   * @param states the state set
   * @param <S> the type of the states
   * @return a set containing all acceptance indices
   */
  public static <S> BitSet getAcceptanceSets(Automaton<S, ?> automaton, Set<S> states) {
    BitSet set = new BitSet();

    for (S state : states) {
      automaton.forEachEdge(state, edge -> {
        if (states.contains(edge.successor())) {
          edge.acceptanceSetIterator().forEachRemaining((IntConsumer) set::set);
        }
      });
    }

    return set;
  }
}
