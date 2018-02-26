package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import jhoafparser.consumer.HOAConsumerException;
import jhoafparser.consumer.HOAConsumerStore;
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
import owl.collections.ValuationSetUtil;
import owl.factories.FactorySupplier;
import owl.factories.ValuationSetFactory;

public final class AutomatonReader {
  private AutomatonReader() {
  }

  public static HOAConsumerStore getConsumer(Consumer<? super Automaton<HoaState, ?>> consumer,
    FactorySupplier factory) {
    return new HoaConsumerAutomatonSupplier(consumer, factory);
  }

  public static HOAConsumerStore getConsumer(Consumer<? super Automaton<HoaState, ?>> consumer,
    ValuationSetFactory factory) {
    return new HoaConsumerAutomatonFactory(consumer, factory);
  }

  public static void readHoa(InputStream stream, Consumer<Automaton<HoaState, ?>> consumer,
    FactorySupplier factorySupplier) throws ParseException {
    HOAFParserSettings settings = new HOAFParserSettings();
    HOAFParser.parseHOA(stream, () ->
      new ToTransitionAcceptance(getConsumer(consumer, factorySupplier)), settings);
  }

  public static void readHoa(InputStream stream, Consumer<Automaton<HoaState, ?>> consumer,
    ValuationSetFactory vsFactory) throws ParseException {
    HOAFParserSettings settings = new HOAFParserSettings();
    HOAFParser.parseHOA(stream, () ->
      new ToTransitionAcceptance(getConsumer(consumer, vsFactory)), settings);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(String input,
    FactorySupplier factorySupplier, Class<A> acceptanceClass) throws ParseException {
    return readHoa(toStream(input), factorySupplier, acceptanceClass);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(String input,
    ValuationSetFactory vsFactory, Class<A> acceptanceClass) throws ParseException {
    return readHoa(toStream(input), vsFactory, acceptanceClass);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(InputStream stream,
    FactorySupplier factorySupplier, Class<A> acceptanceClass) throws ParseException {
    List<Automaton<HoaState, A>> automata = new ArrayList<>();
    Consumer<Automaton<HoaState, ?>> automatonConsumer = automaton ->
      automata.add(AutomatonUtil.cast(automaton, HoaState.class, acceptanceClass));
    readHoa(stream, automatonConsumer, factorySupplier);
    return Iterables.getOnlyElement(automata);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoa(InputStream stream,
    ValuationSetFactory vsFactory, Class<A> acceptanceClass) throws ParseException {
    List<Automaton<HoaState, A>> automata = new ArrayList<>();
    Consumer<Automaton<HoaState, ?>> automatonConsumer = automaton ->
      automata.add(AutomatonUtil.cast(automaton, HoaState.class, acceptanceClass));
    readHoa(stream, automatonConsumer, vsFactory);
    return Iterables.getOnlyElement(automata);
  }

  public static List<Automaton<HoaState, ?>> readHoaCollection(
    InputStream input, FactorySupplier factorySupplier) throws ParseException {
    List<Automaton<HoaState, ?>> automatonList = new ArrayList<>();
    readHoa(input, automatonList::add, factorySupplier);
    return automatonList;
  }

  public static List<Automaton<HoaState, ?>> readHoaCollection(String input,
    FactorySupplier factorySupplier) throws ParseException {
    return readHoaCollection(toStream(input), factorySupplier);
  }

  private static InputStream toStream(String string) {
    return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
  }

  private static final class HoaConsumerAutomatonFactory extends HOAConsumerStore {
    private final Consumer<? super Automaton<HoaState, ?>> consumer;
    private final ValuationSetFactory vsFactory;

    HoaConsumerAutomatonFactory(Consumer<? super Automaton<HoaState, ?>> consumer,
      ValuationSetFactory vsFactory) {
      this.consumer = consumer;
      this.vsFactory = vsFactory;
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
      super.notifyEnd();
      consumer.accept(new StoredConverter(getStoredAutomaton(), vsFactory).transform());
    }
  }

  private static final class HoaConsumerAutomatonSupplier extends HOAConsumerStore {
    private final Consumer<? super Automaton<HoaState, ?>> consumer;
    private final FactorySupplier factory;

    HoaConsumerAutomatonSupplier(Consumer<? super Automaton<HoaState, ?>> consumer,
      FactorySupplier factory) {
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

    StoredConverter(StoredAutomaton storedAutomaton, ValuationSetFactory vsFactory)
      throws HOAConsumerException {
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
      this.vsFactory = vsFactory;
      automaton = new HashMapAutomaton<>(this.vsFactory, acceptance);
      String name = storedAutomaton.getStoredHeader().getName();
      if (name != null) {
        automaton.setName(name);
      }
      states = new Int2ObjectLinkedOpenHashMap<>(storedAutomaton.getNumberOfStates());
    }

    StoredConverter(StoredAutomaton storedAutomaton, FactorySupplier factorySupplier)
      throws HOAConsumerException {
      this(storedAutomaton,
        factorySupplier.getValuationSetFactory(storedAutomaton.getStoredHeader().getAPs()));
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
              throw new HOAConsumerException("Unkown priority " + stringPriority);
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
          return new EmersonLeiAcceptance(numberOfAcceptanceSets, acceptanceExpression);

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
            ValuationSet valuationSet =
              ValuationSetUtil.toValuationSet(vsFactory, edgeWithLabel.getLabelExpr(), apMapping);
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
