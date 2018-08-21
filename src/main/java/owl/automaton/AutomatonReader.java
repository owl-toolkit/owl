/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerStore;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.generated.ParseException;
import jhoafparser.storage.StoredAutomaton;
import jhoafparser.storage.StoredEdgeImplicit;
import jhoafparser.storage.StoredEdgeWithLabel;
import jhoafparser.storage.StoredHeader;
import jhoafparser.storage.StoredHeader.NameAndExtra;
import jhoafparser.storage.StoredState;
import jhoafparser.transformations.ToTransitionAcceptance;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;

public final class AutomatonReader {
  private AutomatonReader() {
  }

  public static void readHoaStream(String string,
    Function<List<String>, ValuationSetFactory> factorySupplier,
    Consumer<Automaton<HoaState, ?>> consumer) throws ParseException {
    readHoaStream(new StringReader(string), factorySupplier, consumer);
  }

  public static void readHoaStream(Reader reader,
    Function<List<String>, ValuationSetFactory> factorySupplier,
    Consumer<Automaton<HoaState, ?>> consumer) throws ParseException {
    HOAFParser.parseHOA(reader, () -> new ToTransitionAcceptance(
      new HoaConsumerAutomatonSupplier(consumer, factorySupplier)), null);
  }

  public static Automaton<HoaState, OmegaAcceptance> readHoa(String string,
    Function<List<String>, ValuationSetFactory> factorySupplier) throws ParseException {
    return readHoa(new StringReader(string), factorySupplier);
  }

  public static Automaton<HoaState, OmegaAcceptance> readHoa(Reader reader,
    Function<List<String>, ValuationSetFactory> factorySupplier) throws ParseException {
    return readHoa(reader, factorySupplier, OmegaAcceptance.class);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(String input,
    Function<List<String>, ValuationSetFactory> factorySupplier, Class<A> acceptanceClass)
    throws ParseException {
    return readHoa(new StringReader(input), factorySupplier, acceptanceClass);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(Reader stream,
    Function<List<String>, ValuationSetFactory> factorySupplier, Class<A> acceptanceClass)
    throws ParseException {
    AtomicReference<Automaton<HoaState, A>> automaton = new AtomicReference<>();
    readHoaStream(stream, factorySupplier, consumer(automaton, acceptanceClass));
    return automaton.get();
  }

  private static <A extends OmegaAcceptance> Consumer<Automaton<HoaState, ?>> consumer(
    AtomicReference<Automaton<HoaState, A>> box, Class<A> acceptanceClass) {
    return automaton -> {
      var oldValue = box.getAndSet(AutomatonUtil.cast(automaton, HoaState.class, acceptanceClass));

      if (oldValue != null) {
        throw new IllegalArgumentException(
          String.format("Stream contained at least two automata: %s, %s", automaton, oldValue));
      }
    };
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

      OmegaAcceptance acceptance = getAcceptance();
      automaton = new HashMapAutomaton<>(this.vsFactory, acceptance);
      String name = storedAutomaton.getStoredHeader().getName();
      if (name != null) {
        automaton.name(name);
      }
      states = new Int2ObjectLinkedOpenHashMap<>(storedAutomaton.getNumberOfStates());
    }

    static void check(boolean condition) throws HOAConsumerException {
      check(condition, "", (Object[]) null);
    }

    static void check(boolean condition, String message) throws HOAConsumerException {
      check(condition, message, (Object[]) null);
    }

    static void check(boolean condition, String formatString, @Nullable Object... args)
      throws HOAConsumerException {
      if (!condition) {
        String message;

        if (Strings.isNullOrEmpty(formatString) || args == null || args.length == 0) {
          message = "";
        } else {
          message = String.format(formatString, args);
        }

        throw new HOAConsumerException(message);
      }
    }

    private void addEdge(HoaState source, ValuationSet valuationSet,
      @Nullable List<Integer> storedEdgeAcceptance, HoaState successor)
      throws HOAConsumerException {
      Edge<HoaState> edge = storedEdgeAcceptance == null
        ? Edge.of(successor)
        : Edge.of(successor, BitSets.of(storedEdgeAcceptance));
      check(automaton.acceptance().isWellFormedEdge(edge));
      automaton.addEdge(source, valuationSet, edge);
    }

    private OmegaAcceptance getAcceptance() throws HOAConsumerException {
      BooleanExpression<AtomAcceptance> acceptanceExpression =
        storedHeader.getAcceptanceCondition();
      int numberOfAcceptanceSets = storedHeader.getNumberOfAcceptanceSets();

      List<NameAndExtra<List<Object>>> acceptanceNames = storedHeader.getAcceptanceNames();
      checkState(acceptanceNames.size() == 1);
      NameAndExtra<List<Object>> acceptanceDescription = acceptanceNames.get(0);
      List<Object> acceptanceExtra = acceptanceDescription.extra;

      switch (acceptanceDescription.name.toLowerCase(Locale.ENGLISH)) {
        case "all":
          return AllAcceptance.INSTANCE;

        case "none":
          return NoneAcceptance.INSTANCE;

        case "buchi":
          return BuchiAcceptance.INSTANCE;

        case "parity":
          check(acceptanceExtra.size() == 3);

          String stringPriority = acceptanceExtra.get(0).toString();
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

          String stringParity = acceptanceExtra.get(1).toString();
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

          String stringColours = acceptanceExtra.get(2).toString();
          int colours;
          try {
            colours = Integer.valueOf(stringColours);
          } catch (NumberFormatException e) {
            throw (HOAConsumerException) new HOAConsumerException(
              "Failed to parse colours " + stringColours).initCause(e);
          }
          check(colours >= 0, "Negative colours");
          check(colours == numberOfAcceptanceSets, "Mismatch between colours (%d) and acceptance"
            + " set count (%d)", colours, numberOfAcceptanceSets);

          return new ParityAcceptance(numberOfAcceptanceSets, Parity.of(max, even));

        case "co-buchi":
          // acc-name: co-Buchi
          // Acceptance: 1 Fin(0)
          return CoBuchiAcceptance.INSTANCE;

        case "generalized-buchi":
          // acc-name: generalized-Buchi 3
          // Acceptance: 3 Inf(0)&Inf(1)&Inf(2)
          int sets = Integer.parseInt(acceptanceExtra.get(0).toString());
          return GeneralizedBuchiAcceptance.of(sets);

        case "generalized-co-buchi":
          // acc-name: generalized-co-Buchi 3
          // Acceptance: 3 Fin(0)|Fin(1)|Fin(2)
          return new EmersonLeiAcceptance(numberOfAcceptanceSets, acceptanceExpression);

        case "streett":
          // acc-name: Streett 3
          // Acceptance: 6 (Fin(0)|Inf(1))&(Fin(2)|Inf(3))&(Fin(4)|Inf(5))
          return new EmersonLeiAcceptance(numberOfAcceptanceSets, acceptanceExpression);

        case "rabin":
          // acc-name: Rabin 3
          // Acceptance: 6 (Fin(0)&Inf(1))|(Fin(2)&Inf(3))|(Fin(4)&Inf(5))
          return RabinAcceptance.of(acceptanceExpression);

        case "generalized-rabin":
          // acc-name: generalized-Rabin 2 3 2
          // Acceptance: 7 (Fin(0)&Inf(1)&Inf(2)&Inf(3))|(Fin(4)&Inf(5)&Inf(6))
          return GeneralizedRabinAcceptance.of(acceptanceExpression);

        default:
          return new EmersonLeiAcceptance(numberOfAcceptanceSets, acceptanceExpression);
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
          for (StoredEdgeImplicit implicitEdge : edgesImplicit) {
            check(counter < (1L << storedHeader.getAPs().size()));

            HoaState successorState = getSuccessor(implicitEdge.getConjSuccessors());

            // TODO Pretty sure we have to remap here, too?
            ValuationSet valuationSet = vsFactory.of(BooleanExpression.fromImplicit(counter));
            List<Integer> edgeAcceptance = implicitEdge.getAccSignature();
            addEdge(state, valuationSet, edgeAcceptance, successorState);
            counter += 1;
          }

          check(counter == (1L << storedHeader.getAPs().size()));
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
