/*
 * Copyright (C) 2020, 2022  (Salomon Sickert)
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

package owl.cinterface;

import static com.google.common.base.Preconditions.checkArgument;
import static org.graalvm.word.WordFactory.nullPointer;
import static owl.cinterface.CAutomaton.AutomatonWrapper.INITIAL;

import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AlternatingCycleDecomposition;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AutomatonWithZielonkaTreeLookup;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.ZielonkaState;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.LabelledFormula;
import owl.thirdparty.jhoafparser.parser.generated.ParseException;
import owl.translations.LtlTranslationRepository;
import owl.translations.LtlTranslationRepository.LtlToDpaTranslation;
import owl.translations.LtlTranslationRepository.LtlToDraTranslation;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import owl.translations.ltl2dela.NormalformDELAConstruction;
import owl.translations.ltl2dela.NormalformDELAConstruction.State;
import owl.translations.ltl2dpa.NormalformDPAConstruction;

@SuppressWarnings("PMD.CouplingBetweenObjects")
@CContext(CInterface.CDirectives.class)
public final class CAutomaton {

  private static final String NAMESPACE = "automaton_";
  private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");

  private CAutomaton() {
  }

  @CEntryPoint(
      name = NAMESPACE + "parse",
      documentation = {
          "Read a (deterministic) automaton from a char* serialised in the HOA format.",
          CInterface.CHAR_TO_STRING,
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle parse(
      IsolateThread thread,
      CCharPointer cCharPointer)
      throws ParseException {

    var hoa = CTypeConversion.toJavaString(cCharPointer);

    // Parse controllable-AP

    BitSet controllableAPIndices = new BitSet();

    {
      String fieldName = "controllable-AP: ";
      int controllableAPStringIndex = hoa.indexOf(fieldName);

      if (controllableAPStringIndex >= 0) {
        String begin = hoa.substring(controllableAPStringIndex + fieldName.length());
        String indices = begin.substring(0, begin.indexOf('\n'));

        for (String index : WHITE_SPACE.split(indices)) {
          controllableAPIndices.set(Integer.parseInt(index));
        }
      }
    }

    // Parse AP:

    List<String> atomicPropositions;

    {
      String fieldName = "AP: ";
      int APStringIndex = hoa.indexOf(fieldName);

      checkArgument(APStringIndex >= 0, "Malformed HOA file.");

      String begin = hoa.substring(APStringIndex + fieldName.length());
      String indices = begin.substring(0, begin.indexOf('\n'));
      String[] splitString = WHITE_SPACE.split(indices);

      int size = Integer.parseInt(splitString[0]);

      atomicPropositions = new ArrayList<>(
          Arrays.asList(splitString).subList(1, splitString.length));

      checkArgument(atomicPropositions.size() == size, "Malformed HOA file.");
    }

    List<String> reorderedAtomicPropositions = new ArrayList<>();
    List<String> uncontrollableAp = new ArrayList<>();
    List<String> controllableAp = new ArrayList<>();

    for (int i = 0, s = atomicPropositions.size(); i < s; i++) {
      if (controllableAPIndices.get(i)) {
        controllableAp.add(atomicPropositions.get(i));
      } else {
        uncontrollableAp.add(atomicPropositions.get(i));
      }
    }

    reorderedAtomicPropositions.addAll(uncontrollableAp);
    reorderedAtomicPropositions.addAll(controllableAp);

    var automaton = AutomatonWrapper.of(
        HoaReader.read(hoa, FactorySupplier.defaultSupplier()::getBddSetFactory,
            List.copyOf(reorderedAtomicPropositions)), uncontrollableAp.size());
    return ObjectHandles.getGlobal().create(automaton);
  }

  @CEntryPoint(
      name = NAMESPACE + "of",
      documentation = {
          "Translate the given formula to deterministic parity automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation) {

    return of(cLabelledFormula, translation, Set.of(), -1);
  }

  // Varargs-simulation

  @CEntryPoint(
      name = NAMESPACE + "of0",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead) {

    return of(cLabelledFormula, translation, Set.of(), lookahead);
  }

  @CEntryPoint(
      name = NAMESPACE + "of1",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead,
      LtlTranslationRepository.Option o1) {

    return of(cLabelledFormula, translation, Set.of(o1), lookahead);
  }

  @CEntryPoint(
      name = NAMESPACE + "of2",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead,
      LtlTranslationRepository.Option o1,
      LtlTranslationRepository.Option o2) {

    return of(cLabelledFormula, translation, Set.of(o1, o2), lookahead);
  }

  @CEntryPoint(
      name = NAMESPACE + "of3",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead,
      LtlTranslationRepository.Option o1,
      LtlTranslationRepository.Option o2,
      LtlTranslationRepository.Option o3) {

    return of(cLabelledFormula, translation, Set.of(o1, o2, o3), lookahead);
  }

  @CEntryPoint(
      name = NAMESPACE + "of4",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead,
      LtlTranslationRepository.Option o1,
      LtlTranslationRepository.Option o2,
      LtlTranslationRepository.Option o3,
      LtlTranslationRepository.Option o4) {

    return of(cLabelledFormula, translation, Set.of(o1, o2, o3, o4), lookahead);
  }

  @CEntryPoint(
      name = NAMESPACE + "of5",
      documentation = {
          "Translate the given formula to an automaton.",
          CInterface.CALL_DESTROY
      }
  )
  public static ObjectHandle of(
      IsolateThread thread,
      ObjectHandle cLabelledFormula,
      int translation,
      int lookahead,
      LtlTranslationRepository.Option o1,
      LtlTranslationRepository.Option o2,
      LtlTranslationRepository.Option o3,
      LtlTranslationRepository.Option o4,
      LtlTranslationRepository.Option o5) {

    return of(cLabelledFormula, translation, Set.of(o1, o2, o3, o4, o5), lookahead);
  }

  private static ObjectHandle of(ObjectHandle cLabelledFormula,
      int translation,
      Set<LtlTranslationRepository.Option> translationOptions,
      int lookahead) {

    OptionalInt lookaheadOptional = lookahead >= 0
        ? OptionalInt.of(lookahead)
        : OptionalInt.empty();

    Function<LabelledFormula, ? extends Automaton<?, ?>> translationFunction;

    LtlToDpaTranslation dpaTranslation = LtlToDpaTranslation.fromCValue(translation);
    LtlToDraTranslation draTranslation = LtlToDraTranslation.fromCValue(translation);

    if (dpaTranslation != null) {
      assert draTranslation == null;
      translationFunction = dpaTranslation
          .translation(ParityAcceptance.class, translationOptions, lookaheadOptional);
    } else if (draTranslation != null) {
      translationFunction = draTranslation
          .translation(RabinAcceptance.class, translationOptions, lookaheadOptional);
    } else {
      throw new IllegalArgumentException("Selected unknown translation");
    }

    var formula = ObjectHandles.getGlobal().<LabelledFormula>get(cLabelledFormula);
    var automaton = translationFunction.apply(formula);
    return ObjectHandles.getGlobal().create(AutomatonWrapper.of(automaton, -1));
  }

  @CEntryPoint(
      name = NAMESPACE + "acceptance_condition"
  )
  public static Acceptance acceptanceCondition(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).acceptance;
  }

  @CEntryPoint(
      name = NAMESPACE + "acceptance_condition_sets"
  )
  public static int acceptanceConditionSets(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).automaton.acceptance().acceptanceSets();
  }

  @CEntryPoint(
      name = NAMESPACE + "atomic_propositions"
  )
  public static int atomicPropositions(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).automaton.atomicPropositions().size();
  }

  @CEntryPoint(
      name = NAMESPACE + "atomic_propositions_uncontrollable_size",
      documentation = "Atomic propositions of the range [0, s[ are uncontrollable and [s, l[ are "
          + "controllable, where s is the value returned by this method. -1 is the default return "
          + "value, when this value cannot be determined."
  )
  public static int atomicPropositionsUncontrollableSize(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).uncontrollableApSize;
  }

  @CEntryPoint(
      name = NAMESPACE + "atomic_propositions_label"
  )
  public static UnsignedWord atomicPropositions(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton,
      int index,
      CCharPointer buffer,
      UnsignedWord bufferSize) {

    return CTypeConversion.toCString(
        get(cDeterministicAutomaton).automaton.atomicPropositions().get(index), buffer, bufferSize);
  }

  @CEntryPoint(
      name = NAMESPACE + "edge_tree",
      documentation = {
          "Serialise the edges leaving the given state into a tree buffer, edge buffer, and an ",
          "optional score buffer. If the scores are not required, the pointer may be set to NULL.",
          "The pointer returned via the vector_{int,double}_t structures must be freed using",
          "the method `free_unmanaged_memory`."
      }
  )
  public static void edgeTree(
      IsolateThread thread,
      ObjectHandle cDeterministicAutomaton,
      int state,
      CIntVector cTreeBuffer,
      CIntVector cEdgeBuffer,
      CDoubleVector cScoreBuffer) {

    boolean computeScores = cScoreBuffer.isNonNull();
    var tree = get(cDeterministicAutomaton).edgeTree(state, computeScores);

    tree.tree.moveTo(cTreeBuffer);
    tree.edges.moveTo(cEdgeBuffer);

    if (tree.scores != null) {
      tree.scores.moveTo(cScoreBuffer);
    }
  }

  @CEntryPoint(
      name = CAutomaton.NAMESPACE + "extract_features_normal_form_zielonka_construction",
      documentation = {
          "Returns a feature vector of the same length as the passed state vector. The memory is ",
          "managed by Java and at the moment there is no API-call to deallocate it."
      }
  )
  @SuppressWarnings("unchecked")
  public static ZielonkaNormalFormState extractFeatures(
      IsolateThread thread, ObjectHandle automatonObjectHandle, CIntVector stateIds) {

    var automaton = get(automatonObjectHandle);

    checkArgument(stateIds.isNonNull());

    int size = stateIds.size();
    checkArgument(size >= 0);

    var zielonkaAutomaton = (AutomatonWithZielonkaTreeLookup<
        ZielonkaState<NormalformDELAConstruction.State>, ParityAcceptance>) automaton.automaton;

    ZielonkaNormalFormState decomposedStates = size == 0
        ? nullPointer()
        : org.graalvm.nativeimage.UnmanagedMemory
            .malloc(SizeOf.unsigned(ZielonkaNormalFormState.class).multiply(size));

    // Extract states.
    List<ZielonkaState<NormalformDELAConstruction.State>> states = new ArrayList<>();
    Set<PropositionalFormula<Integer>> stateFormulas = new HashSet<>();
    Map<Integer, EquivalenceClassEncoder> encoders = new HashMap<>();

    for (int i = 0; i < size; i++) {
      int stateId = stateIds.elements().read(i);

      var uncastedState = stateId == INITIAL && automaton.index2StateMap.isEmpty()
          ? automaton.automaton.initialState()
          : automaton.index2StateMap.get(stateId);

      if (!(uncastedState instanceof ZielonkaState)) {
        throw new IllegalArgumentException(
            "feature extraction only works for 'unpublished zielonka'");
      }

      var state = (ZielonkaState<NormalformDELAConstruction.State>) uncastedState;
      states.add(state);
      stateFormulas.add(state.state().stateFormula());
      Streams.forEachPair(
          state.state().stateMapKeys().stream(),
          state.state().stateMapValues().stream(),
          (key, breakPointState) -> encoders
              .computeIfAbsent(key, x -> new EquivalenceClassEncoder())
              .put(breakPointState));
    }

    List<PropositionalFormula<Integer>> stateFormulasSorted = new ArrayList<>(stateFormulas);
    stateFormulasSorted.sort(Comparator.comparingInt(PropositionalFormula::height));

    for (int i = 0; i < size; i++) {
      // Load state fields.
      var state = states.get(i);
      var stateFormula = state.state().stateFormula();
      State state1 = state.state();
      Set<Entry<Integer, BreakpointStateRejecting>> stateMap = new HashSet<>();
      Streams.forEachPair(state1.stateMapKeys().stream(), state1.stateMapValues().stream(),
          (x, y) -> stateMap.add(Map.entry(x, y)));
      var roundRobinCounters = state.state().roundRobinCounters();
      var zielonkaPath = state.path();
      var zielonkaTree = zielonkaAutomaton.lookup(state);

      // If we use ACD, we can project path to subtree.
      if (zielonkaTree instanceof AlternatingCycleDecomposition) {
        var acd = (AlternatingCycleDecomposition<NormalformDELAConstruction.State>) zielonkaTree;
        zielonkaPath = acd.restrictPathToSubtree(state.state(), zielonkaPath);
      }

      // Store into struct.
      var decomposedState = decomposedStates.addressOf(i);
      decomposedState.stateFormula(stateFormulasSorted.indexOf(stateFormula));
      decomposedState.roundRobinCounters(CIntVectors.copyOf(roundRobinCounters));
      decomposedState.zielonkaPath(CIntVectors.copyOf(zielonkaPath));

      var iterator = stateMap.iterator();
      ZielonkaNormalFormState.StateMapEntry entries = iterator.hasNext()
          ? org.graalvm.nativeimage.UnmanagedMemory.malloc(
          SizeOf.unsigned(ZielonkaNormalFormState.StateMapEntry.class).multiply(stateMap.size()))
          : nullPointer();

      decomposedState.stateMap(entries);
      decomposedState.stateMapSize(stateMap.size());

      for (int j = 0, s = stateMap.size(); j < s; j++) {
        var entry = iterator.next();
        var cEntry = entries.addressOf(j);

        // Copy fields
        var dbwState = entry.getValue();
        var encoder = encoders.get(entry.getKey());
        cEntry.key(entry.getKey());
        cEntry.allProfile(CIntVectors.copyOf(encoder.getAllProfile(dbwState)));
        cEntry.rejectingProfile(CIntVectors.copyOf(encoder.getRejectingProfile(dbwState)));
        cEntry.disambiguation(encoder.disambiguation(dbwState));
      }

      assert !iterator.hasNext();
    }

    return decomposedStates;
  }

  @CContext(CInterface.CDirectives.class)
  @CStruct("owl_zielonka_normal_form_state")
  interface ZielonkaNormalFormState extends PointerBase {

    // allows access to individual structs in an array
    ZielonkaNormalFormState addressOf(int index);

    /**
     * A non-negative id assigned to the state-formula of the underlying DELW.
     */
    @CField("state_formula")
    int stateFormula();

    @CField("state_formula")
    void stateFormula(int stateFormula);

    /**
     * An array of size `stateMapSize` storing a mapping of indices to states of the underlying DBW.
     * The keys used in the mapping are exactly the variables used in the state formula and are
     * unordered and might be non-continuous.
     */
    @CField("state_map")
    StateMapEntry stateMap();

    @CField("state_map")
    void stateMap(StateMapEntry map);

    /**
     * The size of the array stored in `stateMap`.
     */
    @CField("state_map_size")
    int stateMapSize();

    @CField("state_map_size")
    void stateMapSize(int size);

    /**
     * An ascending sorted list of active round-robin counters. These are computed dynamically and
     * might change from SCC to SCC.
     */
    @CField("round_robin_counters")
    CIntVector roundRobinCounters();

    @CField("round_robin_counters")
    void roundRobinCounters(CIntVector roundRobinCounters);

    /**
     * The current path in the Zielonka-tree.
     */
    @CField("zielonka_path")
    CIntVector zielonkaPath();

    @CField("zielonka_path")
    void zielonkaPath(CIntVector path);

    @CContext(CInterface.CDirectives.class)
    @CStruct("owl_zielonka_normal_form_state_state_map_entry")
    interface StateMapEntry extends PointerBase {

      // allows access to individual structs in an array
      StateMapEntry addressOf(int index);

      /**
       * The id used to map the state into the state-formula.
       */
      @CField("key")
      int key();

      @CField("key")
      void key(int key);

      /**
       * A vector of features for the `all` field of the DBW-state.
       */
      @CField("all_profile")
      CIntVector allProfile();

      @CField("all_profile")
      void allProfile(CIntVector profile);

      /**
       * A vector of features for the `all` field of the DBW-state.
       */
      @CField("rejecting_profile")
      CIntVector rejectingProfile();

      @CField("rejecting_profile")
      void rejectingProfile(CIntVector profile);

      /**
       * A non-negative, unique integer used to disambiguate state from other states mapped to the
       * same all_profile and rejecting_profile.
       */
      @CField("disambiguation")
      int disambiguation();

      @CField("disambiguation")
      void disambiguation(int disambiguation);
    }
  }

  private static AutomatonWrapper<?, ?> get(ObjectHandle cDeterministicAutomaton) {
    return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
  }

  @CEnum("owl_acceptance_condition")
  public enum Acceptance {

    OWL_BUCHI,
    OWL_CO_BUCHI,
    OWL_PARITY_MAX_EVEN,
    OWL_PARITY_MAX_ODD,
    OWL_PARITY_MIN_EVEN,
    OWL_PARITY_MIN_ODD,
    OWL_RABIN;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native Acceptance fromCValue(int value);

    public static Acceptance fromOmegaAcceptance(EmersonLeiAcceptance acceptance) {
      if (acceptance instanceof BuchiAcceptance) {
        return OWL_BUCHI;
      }

      if (acceptance instanceof CoBuchiAcceptance || acceptance instanceof AllAcceptance) {
        return OWL_CO_BUCHI;
      }

      if (acceptance instanceof ParityAcceptance parityAcceptance) {
        if (parityAcceptance.parity().even()) {
          if (parityAcceptance.parity().max()) {
            return OWL_PARITY_MAX_EVEN;
          } else {
            return OWL_PARITY_MIN_EVEN;
          }
        } else {
          if (parityAcceptance.parity().max()) {
            return OWL_PARITY_MAX_ODD;
          } else {
            return OWL_PARITY_MIN_ODD;
          }
        }
      }

      if (acceptance instanceof RabinAcceptance) {
        return OWL_RABIN;
      }

      throw new IllegalArgumentException();
    }
  }

  static final class AutomatonWrapper<S, T> {

    // Public constants
    static final int INITIAL = 0;
    static final int EDGE_DELIMITER = -1;
    static final int EDGE_GROUP_DELIMITER = -2;

    final Automaton<S, ?> automaton;
    final Acceptance acceptance;
    final int uncontrollableApSize;

    // Mapping information
    private final List<S> index2StateMap;
    private final Map<S, Integer> state2indexMap;

    // Additional features for C interface
    private final ToDoubleFunction<? super Edge<S>> qualityScore;

    private <A extends EmersonLeiAcceptance> AutomatonWrapper(
        Automaton<S, ? extends A> automaton,
        Acceptance acceptance,
        ToDoubleFunction<? super Edge<S>> qualityScore,
        int uncontrollableApSize) {

      checkArgument(automaton.initialStates().size() == 1);

      this.automaton = automaton;
      this.acceptance = acceptance;
      this.qualityScore = qualityScore;

      this.index2StateMap = new ArrayList<>();
      this.state2indexMap = new HashMap<>();
      this.uncontrollableApSize = uncontrollableApSize;

      // Ensure that the initial state is assigned 0 in the mapping.
      index(automaton.initialState());

      if (ImageInfo.inImageCode()) {
        boolean consistentDeclarations = INITIAL == CInterface.owlInitialState()
            && EDGE_DELIMITER == CInterface.owlEdgeDelimiter()
            && EDGE_GROUP_DELIMITER == CInterface.owlEdgeGroupDelimiter();

        if (!consistentDeclarations) {
          throw new AssertionError("C headers declare conflicting constants.");
        }
      }
    }

    static <S> AutomatonWrapper<S, ?> of(Automaton<S, ?> automaton, int uncontrollableApSize) {

      ToDoubleFunction<Edge<S>> scoring;

      // Inject scoring if the automaton is known.
      if (automaton instanceof AutomatonWithZielonkaTreeLookup castedAutomaton) {
        scoring = NormalformDPAConstruction.scoringFunction(castedAutomaton);
      } else {
        scoring = x -> 0.5d;
      }

      return new AutomatonWrapper<>(
          automaton,
          Acceptance.fromOmegaAcceptance(automaton.acceptance()),
          scoring,
          uncontrollableApSize
      );
    }

    private int index(S state) {
      Objects.requireNonNull(state);

      Integer index = state2indexMap.get(state);

      if (index == null) {
        index = index2StateMap.size();
        index2StateMap.add(state);
        state2indexMap.put(state, index);
      }

      return index;
    }

    private void serialise(
        MtBdd<Edge<S>> edgeTree,
        SerialisedEdgeTree buffers,
        int treeBufferWriteBackPosition,
        Map<MtBdd<Edge<S>>, Integer> cachedPositions) {

      CIntVectorBuilder treeBuffer = buffers.tree;
      Integer position = cachedPositions.get(edgeTree);

      if (position == null) {
        if (edgeTree instanceof MtBdd.Node<Edge<S>> node) {
          position = treeBuffer.size();
          treeBuffer.add(node.variable, -1, -1);
          serialise(node.falseChild, buffers, position + 1, cachedPositions);
          serialise(node.trueChild, buffers, position + 2, cachedPositions);
        } else if (edgeTree instanceof MtBdd.Leaf<Edge<S>> leaf) {
          position = -(serialise(leaf.value, buffers) + 1);
        } else {
          throw new AssertionError("unreachable");
        }

        cachedPositions.put(edgeTree, position);
      }

      if (treeBufferWriteBackPosition >= 0) {
        treeBuffer.set(treeBufferWriteBackPosition, position);
      }
    }

    private int serialise(Set<Edge<S>> edges, SerialisedEdgeTree buffers) {
      var edgeBuffer = buffers.edges;
      var scoreBuffer = buffers.scores;
      int position = edgeBuffer.size();

      Iterator<Edge<S>> edgeIterator = edges.iterator();

      while (edgeIterator.hasNext()) {
        Edge<S> edge = edgeIterator.next();
        edgeBuffer.add(index(edge.successor()));
        edge.colours().intStream().forEachOrdered(edgeBuffer::add);

        if (edgeIterator.hasNext()) {
          edgeBuffer.add(EDGE_DELIMITER);
        }

        if (scoreBuffer != null) {
          scoreBuffer.add(qualityScore.applyAsDouble(edge));
        }
      }

      edgeBuffer.add(EDGE_GROUP_DELIMITER);
      return position;
    }

    SerialisedEdgeTree edgeTree(int stateIndex, boolean computeScores) {
      // If the automaton accepts everything, then index2stateMap is empty.
      S state = stateIndex == INITIAL && index2StateMap.isEmpty()
          ? automaton.initialState()
          : index2StateMap.get(stateIndex);

      var edgeTree = automaton.edgeTree(state);
      var serialisedEdgeTree = new SerialisedEdgeTree(computeScores);
      serialise(edgeTree, serialisedEdgeTree, -1, new HashMap<>());
      return serialisedEdgeTree;
    }
  }

  static class SerialisedEdgeTree {

    final CIntVectorBuilder tree;
    final CIntVectorBuilder edges;
    @Nullable
    final CDoubleVectorBuilder scores;

    SerialisedEdgeTree(boolean computeScores) {
      this.tree = new CIntVectorBuilder();
      this.edges = new CIntVectorBuilder();
      this.scores = computeScores ? new CDoubleVectorBuilder() : null;
    }
  }
}
