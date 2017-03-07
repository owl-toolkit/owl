package owl.automaton;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.AtomAcceptance.Type;
import jhoafparser.ast.AtomLabel;
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
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Priority;
import owl.automaton.edge.Edge;
import owl.automaton.edge.Edges;
import owl.collections.ValuationSet;
import owl.collections.ValuationSetUtil;
import owl.factories.Registry;
import owl.factories.ValuationSetFactory;

public final class AutomatonReader {
  private AutomatonReader() {
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoaInput(
    String input, Class<A> acceptanceClass) throws ParseException {
    return readHoaInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
      acceptanceClass);
  }

  public static <A extends OmegaAcceptance> Automaton<HoaState, A> readHoaInput(
    InputStream stream, Class<A> acceptanceClass) throws ParseException {
    List<Automaton<HoaState, A>> automata = new ArrayList<>();
    readHoaInput(stream, automaton -> {
      checkArgument(acceptanceClass.isInstance(automaton.getAcceptance()));
      checkArgument(automata.isEmpty());
      //noinspection unchecked
      automata.add((Automaton<HoaState, A>) automaton);
    });
    checkArgument(automata.size() == 1);
    return automata.iterator().next();
  }

  public static Collection<Automaton<HoaState, ?>> readHoaInput(String input)
    throws ParseException {
    return readHoaInput(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
  }

  public static Collection<Automaton<HoaState, ?>> readHoaInput(InputStream stream)
    throws ParseException {
    Collection<Automaton<HoaState, ?>> automatonList = new ArrayList<>();
    readHoaInput(stream, automatonList::add);
    return automatonList;
  }

  public static void readHoaInput(InputStream stream,
    Consumer<Automaton<HoaState, ?>> automatonConsumer) throws ParseException {
    HOAFParser.parseHOA(stream, () -> new HoaConsumerAutomaton(automatonConsumer));
  }

  private static final class HoaConsumerAutomaton extends HOAConsumerStore {
    private final Consumer<Automaton<HoaState, ?>> consumer;

    public HoaConsumerAutomaton(Consumer<Automaton<HoaState, ?>> consumer) {
      this.consumer = consumer;
    }

    @Override
    public void notifyEnd() throws HOAConsumerException {
      super.notifyEnd();
      consumer.accept(new StoredConverter(getStoredAutomaton()).transform());
    }

  }

  public static final class HoaState {
    private final int id;
    @Nullable
    private final String info;

    HoaState(int id) {
      this.id = id;
      this.info = null;
    }

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

    int getId() {
      return id;
    }

    @Nullable
    public String getInfo() {
      return info;
    }

    @Override
    public int hashCode() {
      return 31 * id;
    }

    @Override
    public String toString() {
      if (info == null) {
        return String.valueOf(id);
      }
      return String.format("%d (%s)", id, info);
    }
  }

  private static final class StoredConverter {
    private final MutableAutomaton<HoaState, ?> automaton;
    private final Int2ObjectMap<HoaState> states;
    private final StoredAutomaton storedAutomaton;
    private final StoredHeader storedHeader;
    private final ValuationSetFactory valuationSetFactory;

    StoredConverter(StoredAutomaton storedAutomaton)
      throws HOAConsumerException {
      check(!storedAutomaton.hasUniversalBranching(), "Universal branching not supported");

      this.storedAutomaton = storedAutomaton;
      this.storedHeader = storedAutomaton.getStoredHeader();

      List<String> variables = storedHeader.getAPs();
      if (variables == null) {
        variables = extractVariables(storedAutomaton);
      }

      OmegaAcceptance acceptance = getAcceptance();
      valuationSetFactory =
        Registry.getFactories(variables.size()).valuationSetFactory;
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

    private static List<String> extractVariables(StoredAutomaton storedAutomaton) {
      IntSet apSet = new IntArraySet();
      Streams.stream(storedAutomaton.getStoredStates()) // Over all states
        .flatMap(state -> {
          // Get all expressions related to this state (state and edge labels)
          int stateId = state.getStateId();
          assert !storedAutomaton.hasEdgesImplicit(stateId);
          Stream<BooleanExpression<AtomLabel>> stream;
          if (state.getLabelExpr() == null) {
            stream = Stream.empty();
          } else {
            stream = Stream.of(state.getLabelExpr());
          }
          if (storedAutomaton.hasEdgesWithLabel(stateId)) {
            Stream<StoredEdgeWithLabel> edgeStream =
              Streams.stream(storedAutomaton.getEdgesWithLabel(stateId));
            stream = Stream.concat(stream, edgeStream.map(StoredEdgeWithLabel::getLabelExpr));
          }
          return stream;
        })
        .map(StoredConverter::getAtoms) // Extract all the atoms
        .flatMap(Collection::stream) // Flat to stream of atoms
        .mapToInt(AtomLabel::getAPIndex) // Extract AP indices
        .forEach(apSet::add); // Add to set

      // AP indices might be sparse
      int maxAp = Collections.max(apSet);
      List<String> variables = new ArrayList<>(maxAp);
      for (int i = 0; i < maxAp; i++) {
        // TODO Fill those up with "p" + i?
        variables.add(null);
      }
      apSet.forEach(ap -> variables.set(ap, "p" + ap));
      return variables;
    }

    private static Collection<AtomLabel> getAtoms(BooleanExpression<AtomLabel> expression) {
      Set<AtomLabel> set = new HashSet<>();
      getAtomsRecursive(expression, set);
      return set;
    }

    private static void getAtomsRecursive(BooleanExpression<AtomLabel> expression,
      Collection<AtomLabel> accumulator) {
      if (expression.isFALSE() || expression.isTRUE()) {
        return;
      }
      if (expression.isAtom()) {
        accumulator.add(expression.getAtom());
      } else if (expression.isNOT()) {
        getAtomsRecursive(expression.getLeft(), accumulator);
      } else if (expression.isAND() || expression.isOR()) {
        getAtomsRecursive(expression.getLeft(), accumulator);
        getAtomsRecursive(expression.getRight(), accumulator);
      }
    }

    private void addEdge(HoaState source, @Nullable List<Integer> storedStateAcceptance,
      ValuationSet valuationSet, @Nullable List<Integer> storedEdgeAcceptance,
      HoaState successor) throws HOAConsumerException {
      IntSet acceptanceSet = new IntArraySet();
      if (storedEdgeAcceptance != null) {
        acceptanceSet.addAll(storedEdgeAcceptance);
      }
      if (storedStateAcceptance != null) {
        acceptanceSet.addAll(storedStateAcceptance);
      }
      Edge<HoaState> edge = Edges.create(successor, acceptanceSet.iterator());
      check(automaton.getAcceptance().isWellFormedEdge(edge));
      automaton.addEdge(source, valuationSet, edge);
    }

    private OmegaAcceptance getAcceptance() throws HOAConsumerException {
      BooleanExpression<AtomAcceptance> acceptanceExpression =
        storedHeader.getAcceptanceCondition();
      int numberOfAcceptanceSets = storedHeader.getNumberOfAcceptanceSets();

      List<NameAndExtra<List<Object>>> acceptanceNames =
        storedHeader.getAcceptanceNames();
      checkState(acceptanceNames.size() == 1);
      NameAndExtra<List<Object>> acceptanceDescription = acceptanceNames.get(0);
      String acceptanceName = acceptanceDescription.name;
      List<Object> acceptanceExtraInfo = acceptanceDescription.extra;

      check(acceptanceName != null && acceptanceExtraInfo != null);
      assert acceptanceName != null && acceptanceExtraInfo != null;

      switch (acceptanceName.toLowerCase(Locale.ENGLISH)) {
        case "all":
          check(acceptanceExtraInfo.isEmpty()
            && acceptanceExpression.isTRUE() && numberOfAcceptanceSets == 0);
          return new AllAcceptance();
        case "none":
          check(acceptanceExtraInfo.isEmpty()
            && acceptanceExpression.isFALSE() && numberOfAcceptanceSets == 0);
          return new NoneAcceptance();
        case "buchi":
          check(acceptanceExtraInfo.isEmpty() && numberOfAcceptanceSets == 1);
          check(acceptanceExpression.isAtom()
            && acceptanceExpression.getAtom().getType() == Type.TEMPORAL_INF);
          return new BuchiAcceptance();
        case "parity":
          check(acceptanceExtraInfo.size() == 3);
          String stringComparison = acceptanceExtraInfo.get(0).toString();
          if (!Objects.equals(stringComparison, "min")) {
            throw new HOAConsumerException("Only min priority is supported");
          }

          String stringPriority = acceptanceExtraInfo.get(1).toString();
          Priority priority;
          if (Objects.equals(stringPriority, "even")) {
            priority = Priority.EVEN;
          } else if (Objects.equals(stringPriority, "odd")) {
            priority = Priority.ODD;
          } else {
            throw new HOAConsumerException("Unknown priority " + stringPriority);
          }

          String stringColours = acceptanceExtraInfo.get(2).toString();
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

          return new ParityAcceptance(numberOfAcceptanceSets, priority);
        default:
          throw new HOAConsumerException("Acceptance " + acceptanceName + " not supported");
      }
    }

    private HoaState getSuccessor(List<Integer> successors) throws HOAConsumerException {
      check(successors.size() == 1, "Universal edges not supported");
      return states.get(Iterables.getOnlyElement(successors));
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

        List<Integer> stateAcceptance = storedState.getAccSignature();

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

            addEdge(state, stateAcceptance, valuationSet, edgeAcceptance, successorState);
            counter += 1;
          }

          check(counter == (1L << storedHeader.getAPs().size()));
        } else if (storedAutomaton.hasEdgesWithLabel(stateId)) {
          Iterable<StoredEdgeWithLabel> edgesWithLabel =
            storedAutomaton.getEdgesWithLabel(stateId);
          assert edgesWithLabel != null;

          for (StoredEdgeWithLabel edgeWithLabel : edgesWithLabel) {
            HoaState successorState = getSuccessor(edgeWithLabel.getConjSuccessors());
            ValuationSet valuationSet =
              ValuationSetUtil.toValuationSet(valuationSetFactory, edgeWithLabel.getLabelExpr());
            List<Integer> edgeAcceptance = edgeWithLabel.getAccSignature();

            addEdge(state, stateAcceptance, valuationSet, edgeAcceptance, successorState);
          }
        }
      }

      List<List<Integer>> startStates = storedHeader.getStartStates();
      Collection<HoaState> initialStates = new ArrayList<>(startStates.size());
      for (List<Integer> startState : startStates) {
        check(startState.size() == 1, "Universal initial states not supported");
        initialStates.add(states.get(Iterables.getOnlyElement(startState)));
      }
      automaton.setInitialStates(initialStates);
      return automaton;
    }
  }
}
