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

package owl.automaton.hoa;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Iterables;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerStore;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.storage.StoredAutomaton;
import jhoafparser.storage.StoredEdgeImplicit;
import jhoafparser.storage.StoredEdgeWithLabel;
import jhoafparser.storage.StoredHeader;
import jhoafparser.storage.StoredState;
import jhoafparser.transformations.ToTransitionAcceptance;
import owl.automaton.Automaton;
import owl.automaton.HashMapAutomaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class HoaReader {
  private HoaReader() {
  }

  public static void readStream(Reader reader,
    Function<List<String>, ValuationSetFactory> factorySupplier,
    Consumer<Automaton<HoaState, ?>> consumer) throws ParseException {
    HOAFParser.parseHOA(reader, () -> new ToTransitionAcceptance(
      new HoaConsumerAutomatonSupplier(consumer, factorySupplier)), null);
  }

  public static Automaton<HoaState, ?> read(String string,
    Function<List<String>, ValuationSetFactory> factorySupplier) throws ParseException {
    return read(new StringReader(string), factorySupplier);
  }

  public static Automaton<HoaState, ?> read(Reader reader,
    Function<List<String>, ValuationSetFactory> factorySupplier) throws ParseException {
    AtomicReference<Automaton<HoaState, ?>> reference = new AtomicReference<>();

    readStream(reader, factorySupplier, automaton -> {
      var oldValue = reference.getAndSet(automaton);
      if (oldValue != null) {
        throw new IllegalArgumentException(
          String.format("Stream contained at least two automata: %s, %s", automaton, oldValue));
      }
    });

    var automaton = reference.get();

    if (automaton == null) {
      throw new NoSuchElementException("Stream did not contain an automata.");
    }

    return automaton;
  }

  private static final class HoaConsumerAutomatonSupplier extends HOAConsumerStore {
    private final Consumer<? super Automaton<HoaState, ?>> consumer;
    private final Function<List<String>, ValuationSetFactory> factorySupplier;

    HoaConsumerAutomatonSupplier(Consumer<? super Automaton<HoaState, ?>> consumer,
      Function<List<String>, ValuationSetFactory> factorySupplier) {
      this.consumer = consumer;
      this.factorySupplier = factorySupplier;
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
      super.notifyEnd();
      consumer.accept(new StoredConverter(getStoredAutomaton(), factorySupplier).transform());
    }
  }

  public static final class HoaState {
    final int id;
    @Nullable
    final String info;

    HoaState(int id, @Nullable String info) {
      this.id = id;
      this.info = info;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof HoaState)) {
        return false;
      }

      HoaState that = (HoaState) o;
      return id == that.id;
    }

    @Override
    public int hashCode() {
      return HashCommon.mix(id);
    }

    @Override
    public String toString() {
      return info == null ? Integer.toString(id) : String.format("%d (%s)", id, info);
    }
  }

  private static final class StoredConverter {
    private final MutableAutomaton<HoaState, ?> automaton;
    @Nullable
    private final int[] remapping;
    private final Int2ObjectMap<HoaState> states;
    private final StoredAutomaton storedAutomaton;
    private final StoredHeader storedHeader;
    private final ValuationSetFactory vsFactory;

    StoredConverter(StoredAutomaton storedAutomaton,
      Function<List<String>, ValuationSetFactory> factorySupplier) throws HOAConsumerException {
      this.vsFactory = factorySupplier.apply(storedAutomaton.getStoredHeader().getAPs());
      check(!storedAutomaton.hasUniversalBranching(), "Universal branching not supported");

      this.storedAutomaton = storedAutomaton;
      this.storedHeader = storedAutomaton.getStoredHeader();

      List<String> variables = storedHeader.getAPs();
      List<String> alphabet = vsFactory.alphabet();

      if (variables.equals(alphabet)) {
        remapping = null;
      } else {
        remapping = new int[variables.size()];
        ListIterator<String> variableIterator = variables.listIterator();
        while (variableIterator.hasNext()) {
          int variableIndex = alphabet.indexOf(variableIterator.next());
          checkArgument(variableIndex >= 0);
          remapping[variableIterator.previousIndex()] = variableIndex;
        }
      }

      automaton = HashMapAutomaton.of(acceptance(storedHeader), this.vsFactory);
      String name = storedAutomaton.getStoredHeader().getName();
      if (name != null) {
        automaton.name(name);
      }
      states = new Int2ObjectLinkedOpenHashMap<>(storedAutomaton.getNumberOfStates());
    }

    private static void check(boolean condition, String formatString, Object... args)
      throws HOAConsumerException {
      if (!condition) {
        throw new HOAConsumerException(String.format(formatString, args));
      }
    }

    private void addEdge(HoaState source, ValuationSet valuationSet,
      @Nullable List<Integer> storedEdgeAcceptance, HoaState successor)
      throws HOAConsumerException {
      Edge<HoaState> edge = storedEdgeAcceptance == null
        ? Edge.of(successor)
        : Edge.of(successor, BitSets.of(storedEdgeAcceptance));
      check(automaton.acceptance().isWellFormedEdge(edge),
        "%s is not well-formed for %s", edge, automaton.acceptance());
      automaton.addEdge(source, valuationSet, edge);
    }

    private static OmegaAcceptance acceptance(StoredHeader header) throws HOAConsumerException {
      var name = Iterables.getOnlyElement(header.getAcceptanceNames(), null);
      var expression = header.getAcceptanceCondition();
      int sets = header.getNumberOfAcceptanceSets();

      switch (name == null ? "default" : name.name.toLowerCase(Locale.ENGLISH)) {
        case "all":
          return AllAcceptance.INSTANCE;

        case "buchi":
          return BuchiAcceptance.INSTANCE;

        case "parity":
          check(name.extra.size() == 3, "Malformed parity condition.");

          String stringPriority = name.extra.get(0).toString();
          boolean max;

          switch (stringPriority) {
            case "max":
              max = true;
              break;
            case "min":
              max = false;
              break;
            default:
              throw new HOAConsumerException("Unknown priority " + stringPriority);
          }

          String stringParity = name.extra.get(1).toString();
          boolean even;
          switch (stringParity) {
            case "even":
              even = true;
              break;
            case "odd":
              even = false;
              break;
            default:
              throw new HOAConsumerException("Unknown parity " + stringParity);
          }

          String stringColours = name.extra.get(2).toString();
          int colours;
          try {
            colours = Integer.parseInt(stringColours);
          } catch (NumberFormatException e) {
            throw (HOAConsumerException) new HOAConsumerException(
              "Failed to parse colours " + stringColours).initCause(e);
          }
          check(colours >= 0, "Negative colours");
          check(colours == sets, "Mismatch between colours (%d) and acceptance"
            + " set count (%d)", colours, sets);

          return new ParityAcceptance(sets, Parity.of(max, even));

        case "co-buchi":
          // acc-name: co-Buchi
          // Acceptance: 1 Fin(0)
          return CoBuchiAcceptance.INSTANCE;

        case "generalized-buchi":
          // acc-name: generalized-Buchi 3
          // Acceptance: 3 Inf(0)&Inf(1)&Inf(2)
          return GeneralizedBuchiAcceptance.of(Integer.parseInt(name.extra.get(0).toString()));

        case "generalized-co-buchi":
          // acc-name: generalized-co-Buchi 3
          // Acceptance: 3 Fin(0)|Fin(1)|Fin(2)
          return GeneralizedCoBuchiAcceptance.of(Integer.parseInt(name.extra.get(0).toString()));

        case "streett":
          // acc-name: Streett 3
          // Acceptance: 6 (Fin(0)|Inf(1))&(Fin(2)|Inf(3))&(Fin(4)|Inf(5))
          return new EmersonLeiAcceptance(sets, expression);

        case "rabin":
          // acc-name: Rabin 3
          // Acceptance: 6 (Fin(0)&Inf(1))|(Fin(2)&Inf(3))|(Fin(4)&Inf(5))
          return RabinAcceptance.of(expression);

        case "generalized-rabin":
          // acc-name: generalized-Rabin 2 3 2
          // Acceptance: 7 (Fin(0)&Inf(1)&Inf(2)&Inf(3))|(Fin(4)&Inf(5)&Inf(6))
          return GeneralizedRabinAcceptance.of(expression);

        default:
          if (expression.isFALSE()) {
            return GeneralizedCoBuchiAcceptance.of(0);
          }

          if (expression.isTRUE()) {
            return AllAcceptance.INSTANCE;
          }

          return new EmersonLeiAcceptance(sets, expression);
      }
    }

    private HoaState getSuccessor(List<Integer> successors) throws HOAConsumerException {
      check(successors.size() == 1, "Universal edges not supported");
      return states.get(Iterables.getOnlyElement(successors).intValue());
    }

    MutableAutomaton<HoaState, ?> transform() throws HOAConsumerException {
      for (StoredState storedState : storedAutomaton.getStoredStates()) {
        int stateId = storedState.getStateId();
        HoaState state = new HoaState(stateId, storedState.getInfo());

        assert !states.containsKey(stateId);
        states.put(stateId, state);
      }

      for (List<Integer> startState : storedHeader.getStartStates()) {
        check(startState.size() == 1, "Universal initial states not supported");
        automaton.addInitialState(states.get(Iterables.getOnlyElement(startState).intValue()));
      }

      for (StoredState storedState : storedAutomaton.getStoredStates()) {
        int stateId = storedState.getStateId();
        HoaState state = states.get(stateId);
        automaton.addState(state);
        assert storedState.getAccSignature() == null || storedState.getAccSignature().isEmpty();

        if (storedAutomaton.hasEdgesImplicit(stateId)) {
          assert !storedAutomaton.hasEdgesWithLabel(stateId);
          Iterable<StoredEdgeImplicit> edgesImplicit = storedAutomaton.getEdgesImplicit(stateId);
          assert edgesImplicit != null;

          long counter = 0;
          long numberExpectedEdges = 1L << storedHeader.getAPs().size();
          for (StoredEdgeImplicit implicitEdge : edgesImplicit) {
            assert counter < numberExpectedEdges;

            HoaState successorState = getSuccessor(implicitEdge.getConjSuccessors());

            // TODO Pretty sure we have to remap here, too?
            ValuationSet valuationSet = vsFactory.of(BooleanExpression.fromImplicit(counter));
            List<Integer> edgeAcceptance = implicitEdge.getAccSignature();
            addEdge(state, valuationSet, edgeAcceptance, successorState);
            counter += 1;
          }

          assert counter == numberExpectedEdges;
        } else if (storedAutomaton.hasEdgesWithLabel(stateId)) {
          IntUnaryOperator apMapping = remapping == null ? null : i -> remapping[i];

          for (StoredEdgeWithLabel edgeWithLabel : storedAutomaton.getEdgesWithLabel(stateId)) {
            HoaState successorState = getSuccessor(edgeWithLabel.getConjSuccessors());
            ValuationSet valuationSet = vsFactory.of(edgeWithLabel.getLabelExpr(), apMapping);
            addEdge(state, valuationSet, edgeWithLabel.getAccSignature(), successorState);
          }
        }
      }

      automaton.trim();
      return automaton;
    }
  }
}
