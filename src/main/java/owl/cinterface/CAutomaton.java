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

package owl.cinterface;

import static com.google.common.base.Preconditions.checkArgument;
import static org.graalvm.word.WordFactory.nullPointer;
import static owl.cinterface.CAutomaton.DeterministicAutomatonWrapper.ACCEPTING;
import static owl.cinterface.CAutomaton.DeterministicAutomatonWrapper.INITIAL;
import static owl.cinterface.CAutomaton.DeterministicAutomatonWrapper.REJECTING;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import jhoafparser.parser.generated.ParseException;
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
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AlternatingCycleDecomposition;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.AutomatonWithZielonkaTreeLookup;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.Path;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations.ZielonkaState;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.LabelledFormula;
import owl.translations.LtlTranslationRepository;
import owl.translations.ltl2dela.NormalformDELAConstruction;
import owl.translations.ltl2dpa.NormalformDPAConstruction;

@SuppressWarnings("PMD.CouplingBetweenObjects")
@CContext(CInterface.CDirectives.class)
public final class CAutomaton {

  private static final String NAMESPACE = "automaton_";
  private static final Pattern WHITE_SPACE = Pattern.compile("\\s+");

  private CAutomaton() {}

  @CEntryPoint(
    name = NAMESPACE + "parse",
    documentation = {
      "Read a (deterministic) automaton from a char* serialised in the HOA format.",
      CInterface.CHAR_TO_STRING,
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
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

    var automaton = DeterministicAutomatonWrapper.of(
      HoaReader.read(hoa, FactorySupplier.defaultSupplier()::getBddSetFactory, List.copyOf(reorderedAtomicPropositions)), uncontrollableAp.size());
    return ObjectHandles.getGlobal().create(automaton);
  }

  @CEntryPoint(
    name = NAMESPACE + "of",
    documentation = {
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation) {

    return of(cLabelledFormula, translation, Set.of(), -1);
  }

  // Varargs-simulation

  @CEntryPoint(
    name = NAMESPACE + "of0",
    documentation = {
    "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    int lookahead) {

    return of(cLabelledFormula, translation, Set.of(), lookahead);
  }

  @CEntryPoint(
    name = NAMESPACE + "of1",
    documentation = {
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    int lookahead,
    LtlTranslationRepository.Option o1) {

    return of(cLabelledFormula, translation, Set.of(o1), lookahead);
  }

  @CEntryPoint(
    name = NAMESPACE + "of2",
    documentation = {
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    int lookahead,
    LtlTranslationRepository.Option o1,
    LtlTranslationRepository.Option o2) {

    return of(cLabelledFormula, translation, Set.of(o1, o2), lookahead);
  }

  @CEntryPoint(
    name = NAMESPACE + "of3",
    documentation = {
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    int lookahead,
    LtlTranslationRepository.Option o1,
    LtlTranslationRepository.Option o2,
    LtlTranslationRepository.Option o3) {

    return of(cLabelledFormula, translation, Set.of(o1, o2, o3), lookahead);
  }

  @CEntryPoint(
    name = NAMESPACE + "of4",
    documentation = {
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
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
      "Translate the given formula to deterministic parity automaton.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    int lookahead,
    LtlTranslationRepository.Option o1,
    LtlTranslationRepository.Option o2,
    LtlTranslationRepository.Option o3,
    LtlTranslationRepository.Option o4,
    LtlTranslationRepository.Option o5) {

    return of(cLabelledFormula, translation, Set.of(o1, o2, o3, o4, o5), lookahead);
  }

  private static ObjectHandle of(ObjectHandle cLabelledFormula,
    LtlTranslationRepository.LtlToDpaTranslation translation,
    Set<LtlTranslationRepository.Option> translationOptions,
    int lookahead) {

    OptionalInt lookaheadOptional = lookahead >= 0
      ? OptionalInt.of(lookahead)
      : OptionalInt.empty();

    var formula = ObjectHandles.getGlobal().<LabelledFormula>get(cLabelledFormula);
    var automaton = translation.translation(ParityAcceptance.class, translationOptions, lookaheadOptional).apply(formula);
    return ObjectHandles.getGlobal().create(DeterministicAutomatonWrapper.of(automaton, -1));
  }

  @CEntryPoint(
    name = NAMESPACE + "acceptance_condition",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnAcceptance.class
  )
  public static Acceptance acceptanceCondition(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).acceptance;
  }

  @CEntryPoint(
    name = NAMESPACE + "acceptance_condition_sets",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static int acceptanceConditionSets(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).automaton.acceptance().acceptanceSets();
  }

  @CEntryPoint(
    name = NAMESPACE + "atomic_propositions",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
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
      + "value, when this value cannot be determined.",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static int atomicPropositionsUncontrollableSize(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).uncontrollableApSize;
  }

  @CEntryPoint(
    name = NAMESPACE + "atomic_propositions_label",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnUnsignedWord.class
  )
  public static UnsignedWord atomicPropositions(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton,
    int index,
    CCharPointer buffer,
    UnsignedWord bufferSize){

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
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnVoid.class
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

      ZielonkaState<NormalformDELAConstruction.State> state;

      if (stateId == REJECTING) {
        state = ZielonkaState.of(
          NormalformDELAConstruction.State.of(
            PropositionalFormula.falseConstant(), Map.of(), Set.of()), Path.of());
      } else if (stateId == ACCEPTING) {
        state = ZielonkaState.of(
          NormalformDELAConstruction.State.of(
            PropositionalFormula.trueConstant(), Map.of(), Set.of()), Path.of());
      } else {
        var uncastedState = stateId == INITIAL && automaton.index2StateMap.isEmpty()
          ? automaton.automaton.initialState()
          : automaton.index2StateMap.get(stateId);

        if (!(uncastedState instanceof ZielonkaState)) {
          throw new IllegalArgumentException(
            "feature extraction only works for 'unpublished zielonka'");
        }

        state = (ZielonkaState<NormalformDELAConstruction.State>) uncastedState;
      }

      states.add(state);
      stateFormulas.add(state.state().stateFormula());
      //state.state().stateMap().forEach((key, breakPointState) ->
      //  encoders
      //   .computeIfAbsent(key, x -> new EquivalenceClassEncoder())
      //    .put(breakPointState));
    }

    List<PropositionalFormula<Integer>> stateFormulasSorted = new ArrayList<>(stateFormulas);
    stateFormulasSorted.sort(Comparator.comparingInt(PropositionalFormula::height));

    for (int i = 0; i < size; i++) {
      // Load state fields.
      var state = states.get(i);
      var stateFormula = state.state().stateFormula();
      var stateMap = state.state().stateMap();
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
      decomposedState.zielonkaPath(CIntVectors.copyOf(zielonkaPath.indices()));

      var iterator = stateMap.entrySet().iterator();
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
        // cEntry.allProfile(CIntVectors.copyOf(encoder.getAllProfile(dbwState)));
        // cEntry.rejectingProfile(CIntVectors.copyOf(encoder.getRejectingProfile(dbwState)));
        // cEntry.disambiguation(encoder.disambiguation(dbwState));
      }

      assert !iterator.hasNext();
    }

    return decomposedStates;
  }

  @CContext(CInterface.CDirectives.class)
  @CStruct("zielonka_normal_form_state_t")
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
     * An array of size `stateMapSize` storing a mapping of indices to states of the underlying
     * DBW. The keys used in the mapping are exactly the variables used in the state formula and
     * are unordered and might be non-continuous.
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
    @CStruct("zielonka_normal_form_state_state_map_entry_t")
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
       * A non-negative, unique integer used to disambiguate state from other states mapped to
       * the same all_profile and rejecting_profile.
       */
      @CField("disambiguation")
      int disambiguation();

      @CField("disambiguation")
      void disambiguation(int disambiguation);
    }
  }

  @CEntryPoint(
    name = CAutomaton.NAMESPACE + "extract_features",
    documentation = { "Signature: ",
      "boolean (void* automaton, vector_int_t* states, vector_int_t* features)",
      "Extract features from the given set of states of an automaton. This method returns `true` "
        + "if the features disambiguate the given state set. If `false` is returned, the caller of"
        + " the method needs to disambiguate two states with the same set of features by "
        + "additional means, e.g. by adding extra bits. The caller might request the inclusion of "
        + "the accepting and rejecting sink by adding OWL_ACCEPTING_SINK and OWL_REJECTING_SINK to "
        + "the state set. These states are then added on a best-effort basis. [Some automata do "
        + "not have a canonical accepting and rejecting sinks]",
      "The encoding of the feature vector is as follows:",
      "",
      "|---------------------------------------------------------------------------------------------------------------------|",
      "| int (state) | feature_type_t | ... | OWL_FEATURE_SEPARATOR | feature_type | ... | OWL_SEPARATOR | int (state) | ... |",
      "|---------------------------------------------------------------------------------------------------------------------|",
      "",
      "Features are then encoded as follows.",
      "- PERMUTATION: an int sequence of variable length with no duplicates.",
      "- ROUND_ROBIN_COUNTER: a single int.",
      "- TEMPORAL_OPERATORS_PROFILE: an int sequence of variable length with no duplicates."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnBoolean.class
  )
  public static boolean extractFeatures(
    IsolateThread thread,
    ObjectHandle automatonObjectHandle,
    CIntVector states,
    CIntVector features) {

    if (ImageInfo.inImageCode()) {
      boolean consistentDeclarations = CInterface.SEPARATOR == CInterface.owlSeparator()
        && CInterface.FEATURE_SEPARATOR == CInterface.owlFeatureSeparator();

      if (!consistentDeclarations) {
        throw new AssertionError("C headers declare conflicting constants.");
      }
    }

    var automaton = CAutomaton.get(automatonObjectHandle);

    // Resolve state ids to state objects.
    var stateObjects = new HashSet<>();

    for (int i = 0, s = states.size(); i < s; i++) {
      int state = states.elements().read(i);
      boolean changed;

      if (state >= 0) {
        changed = stateObjects.add(automaton.index2StateMap.get(state));
      } else {
        changed = true;
      }

      checkArgument(changed, "'states' vector contains duplicates.");
    }

    var featuresBuilder = new CIntVectorBuilder();

    try {
      var featuresMap = StateFeatures.extract(stateObjects);

      featuresMap.forEach((state, stateFeatures) -> {
        featuresBuilder.add(automaton.state2indexMap.get(state));

        for (StateFeatures.Feature feature : stateFeatures) {
          featuresBuilder.add(feature.type().getCValue());

          switch (feature.type()) {
            case PERMUTATION:
              featuresBuilder.addAll(feature.permutation());
              break;

            case ROUND_ROBIN_COUNTER:
              featuresBuilder.add(feature.roundRobinCounter());
              break;

            case TEMPORAL_OPERATORS_PROFILE:
              featuresBuilder.addAll(feature.temporalOperatorsProfile());
              break;

            default:
              throw new AssertionError("not reachable.");
          }

          featuresBuilder.add(CInterface.FEATURE_SEPARATOR);
        }

        featuresBuilder.add(CInterface.SEPARATOR);
      });

      featuresBuilder.moveTo(features);
      return Collections3.hasDistinctValues(featuresMap);
    } catch (IllegalArgumentException ex) {
      // We do no support this state type. Return empty vector and indicate that it is unambiguous.
      featuresBuilder.moveTo(features);
      return false;
    }
  }

  private static DeterministicAutomatonWrapper<?, ?> get(ObjectHandle cDeterministicAutomaton) {
    return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
  }

  @CEnum("acceptance_t")
  public enum Acceptance {
    BUCHI, CO_BUCHI, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN, PARITY_MIN_ODD;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native Acceptance fromCValue(int value);

    public static Acceptance fromOmegaAcceptance(EmersonLeiAcceptance acceptance) {
      if (acceptance instanceof BuchiAcceptance) {
        return BUCHI;
      }

      if (acceptance instanceof CoBuchiAcceptance || acceptance instanceof AllAcceptance) {
        return CO_BUCHI;
      }

      if (acceptance instanceof ParityAcceptance) {
        var parityAcceptance = (ParityAcceptance) acceptance;

        if (parityAcceptance.parity().even()) {
          if (parityAcceptance.parity().max()) {
            return PARITY_MAX_EVEN;
          } else {
            return PARITY_MIN_EVEN;
          }
        } else {
          if (parityAcceptance.parity().max()) {
            return PARITY_MAX_ODD;
          } else {
            return PARITY_MIN_ODD;
          }
        }
      }

      throw new IllegalArgumentException();
    }
  }

  static final class DeterministicAutomatonWrapper<S, T> {

    // Public constants
    static final int ACCEPTING = -2;
    static final int REJECTING = -1;
    static final int INITIAL = 0;

    final Automaton<S, ?> automaton;
    final Acceptance acceptance;
    final int uncontrollableApSize;

    // Mapping information
    private final Function<? super S, OptionalInt> sinkDetection;
    private final List<S> index2StateMap;
    private final Map<S, Integer> state2indexMap;

    // Additional features for C interface
    private final ToDoubleFunction<? super Edge<S>> qualityScore;

    private <A extends EmersonLeiAcceptance> DeterministicAutomatonWrapper(
      Automaton<S, ? extends A> automaton,
      Acceptance acceptance,
      Function<? super S, OptionalInt> sinkDetection,
      ToDoubleFunction<? super Edge<S>> qualityScore,
      int uncontrollableApSize) {

      checkArgument(automaton.initialStates().size() == 1);
      assert automaton.is(Automaton.Property.DETERMINISTIC);

      this.automaton = automaton;
      this.acceptance = acceptance;
      this.sinkDetection = sinkDetection;
      this.qualityScore = qualityScore;

      this.index2StateMap = new ArrayList<>();
      this.state2indexMap = new HashMap<>();
      this.uncontrollableApSize = uncontrollableApSize;

      index(automaton.initialState());

      if (ImageInfo.inImageCode()) {
        boolean consistentDeclarations = INITIAL == CInterface.owlInitialState()
          && ACCEPTING == CInterface.owlAcceptingSink()
          && REJECTING == CInterface.owlRejectingSink();

        if (!consistentDeclarations) {
          throw new AssertionError("C headers declare conflicting constants.");
        }
      }
    }

    static <S> DeterministicAutomatonWrapper<S, ?>
      of(Automaton<S, ?> automaton, int uncontrollableApSize) {

      Function<S, OptionalInt> sinkDetection = (S state) -> {
        Set<Set<Edge<S>>> edgeSetSet = automaton.edgeTree(state).values();

        // We have multiple options, thus this not a (trivial) sink state.
        if (edgeSetSet.size() != 1) {
          return OptionalInt.empty();
        }

        Set<Edge<S>> edgeSet = Iterables.getOnlyElement(edgeSetSet);

        // The run stops.
        if (edgeSet.isEmpty()) {
          return OptionalInt.of(REJECTING);
        }

        assert edgeSet.size() == 1 : "automaton is not deterministic";

        Edge<S> edge = Iterables.getOnlyElement(edgeSet);

        if (!edge.successor().equals(state)) {
          return OptionalInt.empty();
        }

        return OptionalInt.of(automaton.acceptance().isAcceptingEdge(edge) ? ACCEPTING : REJECTING);
      };

      ToDoubleFunction<Edge<S>> scoring = x -> 0.5d;

      // Inject scoring if the automaton is known.
      if (automaton instanceof AutomatonWithZielonkaTreeLookup) {
        scoring = NormalformDPAConstruction.scoringFunction(
          (AutomatonWithZielonkaTreeLookup) automaton);
      }

      return new DeterministicAutomatonWrapper<>(
        automaton,
        Acceptance.fromOmegaAcceptance(automaton.acceptance()),
        sinkDetection,
        scoring,
        uncontrollableApSize
      );
    }

    private int index(@Nullable S state) {
      if (state == null) {
        return REJECTING;
      }

      Integer index = state2indexMap.get(state);

      if (index == null) {
        OptionalInt sink = sinkDetection.apply(state);

        if (sink.isEmpty()) {
          index = index2StateMap.size();
          index2StateMap.add(state);
        } else {
          index = sink.getAsInt();
        }

        state2indexMap.put(state, index);
      }

      return index;
    }

    private void serialise(
      MtBdd<Edge<S>> edgeTree,
      SerialisedEdgeTree buffers,
      int treeBufferWriteBackPosition,
      Map<MtBdd<Edge<S>>, Integer> cachedPositions) {

      var treeBuffer = buffers.tree;
      Integer position = cachedPositions.get(edgeTree);

      if (position == null) {

        if (edgeTree instanceof MtBdd.Node) {
          var node = (MtBdd.Node<Edge<S>>) edgeTree;

          position = treeBuffer.size();
          treeBuffer.add(node.variable, -1, -1);

          serialise(node.falseChild, buffers, position + 1, cachedPositions);
          serialise(node.trueChild, buffers, position + 2, cachedPositions);
        } else {
          var edge = Iterables.getOnlyElement(((MtBdd.Leaf<Edge<S>>) edgeTree).value, null);

          var edgeBuffer = buffers.edges;

          position = -((edgeBuffer.size() / 2) + 1);

          if (edge == null) {
            edgeBuffer.add(REJECTING, REJECTING);
          } else {
            edgeBuffer.add(index(edge.successor()), edge.colours().last().orElse(REJECTING));
          }

          var scoreBuffer = buffers.scores;

          if (scoreBuffer != null) {
            scoreBuffer.add(edge == null ? 0.0 : qualityScore.applyAsDouble(edge));
          }
        }

        cachedPositions.put(edgeTree, position);
      }

      if (treeBufferWriteBackPosition >= 0) {
        treeBuffer.set(treeBufferWriteBackPosition, position);
      }
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
