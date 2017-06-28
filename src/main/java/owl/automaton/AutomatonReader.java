package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumer;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerStore;
import jhoafparser.consumer.HOAIntermediateCheckValidity;
import jhoafparser.parser.HOAFParser;
import jhoafparser.parser.HOAFParserSettings;
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
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.GenericAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetUtil;
import owl.factories.Registry;
import owl.factories.ValuationSetFactory;

public final class AutomatonReader {
  private AutomatonReader() {
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(String input,
    Class<A> acceptanceClass) throws ParseException {
    return readHoa(toStream(input), null, acceptanceClass);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(InputStream stream,
    @Nullable ValuationSetFactory factory, Class<A> acceptanceClass) throws ParseException {
    List<Automaton<HoaState, A>> automata = new ArrayList<>();
    Consumer<Automaton<HoaState, ?>> automatonConsumer = automaton -> {
      checkArgument(acceptanceClass.isInstance(automaton.getAcceptance()));
      // noinspection unchecked
      automata.add((Automaton<HoaState, A>) automaton);
    };

    readHoa(stream, automatonConsumer, factory, false);
    return Iterables.getOnlyElement(automata);
  }

  public static void readHoa(InputStream stream, Consumer<Automaton<HoaState, ?>> consumer,
    @Nullable ValuationSetFactory factory, boolean lenient) throws ParseException {
    HOAFParserSettings settings = new HOAFParserSettings();
    // We just try to somehow make sense of the input
    settings.setFlagValidate(lenient);

    HOAFParser.parseHOA(stream, () -> {
      HOAConsumer hoaConsumer =
        new ToTransitionAcceptance(new HoaConsumerAutomaton(consumer, factory));
      return lenient ? hoaConsumer : new HOAIntermediateCheckValidity(hoaConsumer);
    }, settings);
  }

  public static List<Automaton<HoaState, ?>> readHoaCollection(InputStream input)
    throws ParseException {
    List<Automaton<HoaState, ?>> automatonList = new ArrayList<>();
    readHoa(input, automatonList::add, null, false);
    return automatonList;
  }

  public static List<Automaton<HoaState, ?>> readHoaCollection(String input) throws ParseException {
    return readHoaCollection(toStream(input));
  }

  private static InputStream toStream(String string) {
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }

  private static final class HoaConsumerAutomaton extends HOAConsumerStore {
    private final Consumer<Automaton<HoaState, ?>> consumer;
    @Nullable
    private final ValuationSetFactory factory;

    HoaConsumerAutomaton(Consumer<Automaton<HoaState, ?>> consumer,
      @Nullable ValuationSetFactory factory) {
      this.consumer = consumer;
      this.factory = factory;
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
      super.notifyEnd();
      consumer.accept(new StoredConverter(getStoredAutomaton(), factory).transform());
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
      return id;
    }

    @Override
    public String toString() {
      if (info == null) {
        return Integer.toString(id);
      }

      return String.format("%d (%s)", id, info);
    }
  }

  private static final class StoredConverter {
    private final boolean aliasRemap;
    private final MutableAutomaton<HoaState, ?> automaton;
    private final Int2ObjectMap<HoaState> states;
    private final StoredAutomaton storedAutomaton;
    private final StoredHeader storedHeader;
    private final ValuationSetFactory valuationSetFactory;

    StoredConverter(StoredAutomaton storedAutomaton, @Nullable ValuationSetFactory factory)
      throws HOAConsumerException {
      check(!storedAutomaton.hasUniversalBranching(), "Universal branching not supported");

      this.storedAutomaton = storedAutomaton;
      this.storedHeader = storedAutomaton.getStoredHeader();
      this.aliasRemap = factory != null;

      List<String> variables = storedHeader.getAPs();

      OmegaAcceptance acceptance = getAcceptance();
      if (factory == null) {
        valuationSetFactory = Registry.getFactories(variables.size()).valuationSetFactory;
      } else {
        valuationSetFactory = factory;
      }

      automaton = new HashMapAutomaton<>(valuationSetFactory, acceptance);
      automaton.setVariables(variables);
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

      Edge<HoaState> edge;

      if (storedEdgeAcceptance == null) {
        edge = Edges.create(successor);
      } else {
        edge = Edges.create(successor, storedEdgeAcceptance.stream().mapToInt(x -> x).iterator());
      }

      check(automaton.getAcceptance().isWellFormedEdge(edge));
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
          return new AllAcceptance();

        case "none":
          return new NoneAcceptance();

        case "buchi":
          return new BuchiAcceptance();

        case "parity":
          check(acceptanceExtra.size() == 3);
          String stringComparison = acceptanceExtra.get(0).toString();

          if (!Objects.equals(stringComparison, "min")) {
            throw new HOAConsumerException("Only min priority is supported");
          }

          String stringPriority = acceptanceExtra.get(1).toString();
          Priority priority;
          if (Objects.equals(stringPriority, "even")) {
            priority = Priority.EVEN;
          } else if (Objects.equals(stringPriority, "odd")) {
            priority = Priority.ODD;
          } else {
            throw new HOAConsumerException("Unknown priority " + stringPriority);
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
          check(colours == numberOfAcceptanceSets,
            "Mismatch between colours (%d) and acceptance" + " set count (%d)", colours,
            numberOfAcceptanceSets);

          return new ParityAcceptance(numberOfAcceptanceSets, priority);

        case "co-buchi":
          // acc-name: co-Buchi
          // Acceptance: 1 Fin(0)
          return new GenericAcceptance(numberOfAcceptanceSets, acceptanceExpression);

        case "generalized-buchi":
          // acc-name: generalized-Buchi 3
          // Acceptance: 3 Inf(0)&Inf(1)&Inf(2)
          int sets = Integer.parseInt(acceptanceExtra.get(0).toString());
          return new GeneralizedBuchiAcceptance(sets);

        case "generalized-co-buchi":
          // acc-name: generalized-co-Buchi 3
          // Acceptance: 3 Fin(0)|Fin(1)|Fin(2)
          return new GenericAcceptance(numberOfAcceptanceSets, acceptanceExpression);

        case "streett":
          // acc-name: Streett 3
          // Acceptance: 6 (Fin(0)|Inf(1))&(Fin(2)|Inf(3))&(Fin(4)|Inf(5))
          return new GenericAcceptance(numberOfAcceptanceSets, acceptanceExpression);

        case "rabin":
          // acc-name: Rabin 3
          // Acceptance: 6 (Fin(0)&Inf(1))|(Fin(2)&Inf(3))|(Fin(4)&Inf(5))
          return RabinAcceptance.create(acceptanceExpression);

        case "generalized-rabin":
          // acc-name: generalized-Rabin 2 3 2
          // Acceptance: 7 (Fin(0)&Inf(1)&Inf(2)&Inf(3))|(Fin(4)&Inf(5)&Inf(6))
          return GeneralizedRabinAcceptance.create(acceptanceExpression);

        default:
          return new GenericAcceptance(numberOfAcceptanceSets, acceptanceExpression);
      }
    }

    private HoaState getSuccessor(List<Integer> successors) throws HOAConsumerException {
      check(successors.size() == 1, "Universal edges not supported");
      return states.get(Iterables.getOnlyElement(successors).intValue());
    }

    MutableAutomaton<HoaState, ?> transform() throws HOAConsumerException {
      List<String> variables = storedAutomaton.getStoredHeader().getAPs();

      for (StoredState storedState : storedAutomaton.getStoredStates()) {
        int stateId = storedState.getStateId();
        HoaState state = new HoaState(stateId, storedState.getInfo());

        assert !states.containsKey(stateId);
        states.put(stateId, state);
      }

      for (StoredState storedState : storedAutomaton.getStoredStates()) {
        int stateId = storedState.getStateId();
        HoaState state = states.get(stateId);

        assert state != null;
        assert storedState.getAccSignature() == null || storedState.getAccSignature().isEmpty();

        if (storedAutomaton.hasEdgesImplicit(stateId)) {
          assert !storedAutomaton.hasEdgesWithLabel(stateId);
          Iterable<StoredEdgeImplicit> edgesImplicit = storedAutomaton.getEdgesImplicit(stateId);
          assert edgesImplicit != null;

          long counter = 0;
          for (StoredEdgeImplicit implicitEdge : edgesImplicit) {
            check(counter < (1L << storedHeader.getAPs().size()));

            HoaState successorState = getSuccessor(implicitEdge.getConjSuccessors());

            ValuationSet valuationSet =
              valuationSetFactory.createValuationSet(BooleanExpression.fromImplicit(counter));
            List<Integer> edgeAcceptance = implicitEdge.getAccSignature();

            addEdge(state, valuationSet, edgeAcceptance, successorState);
            counter += 1;
          }

          check(counter == (1L << storedHeader.getAPs().size()));
        } else if (storedAutomaton.hasEdgesWithLabel(stateId)) {

          for (StoredEdgeWithLabel edgeWithLabel : storedAutomaton.getEdgesWithLabel(stateId)) {
            HoaState successorState = getSuccessor(edgeWithLabel.getConjSuccessors());
            ValuationSet valuationSet = ValuationSetUtil.toValuationSet(
              valuationSetFactory,
              edgeWithLabel.getLabelExpr(),
              aliasRemap ? (i) -> Integer.parseInt(variables.get(i).substring(1)) : null);

            addEdge(state, valuationSet, edgeWithLabel.getAccSignature(), successorState);
          }
        }
      }

      List<List<Integer>> startStates = storedHeader.getStartStates();
      Collection<HoaState> initialStates = new ArrayList<>(startStates.size());
      for (List<Integer> startState : startStates) {
        check(startState.size() == 1, "Universal initial states not supported");
        initialStates.add(states.get(Iterables.getOnlyElement(startState).intValue()));
      }
      automaton.setInitialStates(initialStates);
      return automaton;
    }
  }
}
