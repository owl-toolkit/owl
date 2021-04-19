/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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
import static owl.cinterface.CAutomaton.Acceptance.BUCHI;
import static owl.cinterface.CAutomaton.Acceptance.CO_BUCHI;
import static owl.cinterface.CAutomaton.Acceptance.PARITY_MIN_EVEN;
import static owl.cinterface.CAutomaton.Acceptance.PARITY_MIN_ODD;
import static owl.cinterface.CAutomaton.Acceptance.SAFETY;
import static owl.cinterface.CAutomaton.DeterministicAutomatonWrapper.ACCEPTING;
import static owl.cinterface.CAutomaton.DeterministicAutomatonWrapper.REJECTING;
import static owl.cinterface.DecomposedDPA.Tree;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_HEURISTIC;

import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.EnumSet;
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
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.UnsignedWord;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.bdd.BddSet;
import owl.bdd.BddSetFactory;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.Collections3;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.translations.LtlTranslationRepository;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateAccepting;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.ltl2dpa.LTL2DPAFunction;

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
    name = NAMESPACE + "edge_tree_precomputed",
    documentation = {
      "Determines if the edge tree for the following state is precomputed by the automaton."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnBoolean.class
  )
  public static boolean edgeTreePrecomputed(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton,
    int state) {

    return get(cDeterministicAutomaton).edgeTreePrecomputed(state);
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
      } else if (state == ACCEPTING && automaton.canonicalAcceptingState != null) {
        changed = stateObjects.add(automaton.canonicalAcceptingState);
      } else if (state == REJECTING && automaton.canonicalRejectingState != null) {
        changed = stateObjects.add(automaton.canonicalRejectingState);
      } else {
        changed = true;
      }

      checkArgument(changed, "'states' vector contains duplicates.");
    }

    var featuresBuilder = new CIntVectorBuilder();

    try {
      var featuresMap = StateFeatures.extract(stateObjects);

      featuresMap.forEach((state, stateFeatures) -> {

        if (state.equals(automaton.canonicalAcceptingState)) {
          featuresBuilder.add(ACCEPTING);
        } else if (state.equals(automaton.canonicalRejectingState)) {
          featuresBuilder.add(REJECTING);
        } else {
          featuresBuilder.add(automaton.state2indexMap.get(state));
        }

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


  @CEntryPoint(
    name = NAMESPACE + "is_singleton",
    documentation = "Returns true if the automaton only has one state, the initial state.",
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnBoolean.class
  )
  public static boolean isSingleton(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton) {

    var initialStateSuccessors = get(cDeterministicAutomaton).initialStateSuccessors;
    return initialStateSuccessors != null
      && Tree.Leaf.ALLOWED_CONJUNCTION_STATES_PATTERN.containsAll(initialStateSuccessors);
  }



  private static DeterministicAutomatonWrapper<?, ?> get(ObjectHandle cDeterministicAutomaton) {
    return ObjectHandles.getGlobal().get(cDeterministicAutomaton);
  }

  @CEnum("acceptance_t")
  public enum Acceptance {
    BUCHI, CO_BUCHI, CO_SAFETY, PARITY, PARITY_MAX_EVEN, PARITY_MAX_ODD, PARITY_MIN_EVEN,
    PARITY_MIN_ODD, SAFETY, WEAK, BOTTOM;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native Acceptance fromCValue(int value);

    public static Acceptance fromOmegaAcceptance(EmersonLeiAcceptance acceptance) {
      if (acceptance instanceof AllAcceptance) {
        return SAFETY;
      }

      if (acceptance instanceof BuchiAcceptance) {
        return BUCHI;
      }

      if (acceptance instanceof CoBuchiAcceptance) {
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

    public Acceptance lub(Acceptance other) {
      if (this == BOTTOM || this == other) {
        return other;
      }

      switch (this) {
        case CO_SAFETY:
          return other == SAFETY ? WEAK : other;

        case SAFETY:
          return other == CO_SAFETY ? WEAK : other;

        case WEAK:
          return (other == SAFETY || other == CO_SAFETY) ? this : other;

        case BUCHI:
        case CO_BUCHI:
          return (other == CO_SAFETY || other == SAFETY || other == WEAK) ? this : PARITY;

        default:
          return PARITY;
      }
    }

    public boolean isLessThanParity() {
      return this == BUCHI || this == CO_BUCHI || isLessOrEqualWeak();
    }

    public boolean isLessOrEqualWeak() {
      return this == CO_SAFETY || this == SAFETY || this == WEAK || this == BOTTOM;
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
    private final Function<? super S, ? extends T> canonicalizer;
    private final Map<T, Integer> canonicalObjectId;

    // Initial state caching.
    @Nullable
    final MtBdd<Edge<S>> initialStateEdgeTree;
    @Nullable
    final Set<Integer> initialStateSuccessors;

    @Nullable
    BddSet filter;

    @Nullable
    final S canonicalAcceptingState;

    @Nullable
    final S canonicalRejectingState;

    private <A extends EmersonLeiAcceptance> DeterministicAutomatonWrapper(
      Automaton<S, ? extends A> automaton,
      Acceptance acceptance,
      @Nullable Class<? extends A> acceptanceClassBound,
      Function<? super S, OptionalInt> sinkDetection,
      Function<? super S, ? extends T> canonicalizer,
      ToDoubleFunction<? super Edge<S>> qualityScore,
      int uncontrollableApSize,
      @Nullable S canonicalAcceptingState,
      @Nullable S canonicalRejectingState) {

      checkArgument(automaton.initialStates().size() == 1);
      checkArgument(acceptanceClassBound == null
        || acceptanceClassBound.isInstance(automaton.acceptance()));

      assert automaton.is(Automaton.Property.DETERMINISTIC);

      this.automaton = automaton;
      this.acceptance = acceptance;
      this.sinkDetection = sinkDetection;
      this.qualityScore = qualityScore;

      this.index2StateMap = new ArrayList<>();
      this.state2indexMap = new HashMap<>();
      this.canonicalObjectId = new HashMap<>();

      this.canonicalizer = canonicalizer;
      this.uncontrollableApSize = uncontrollableApSize;

      index(automaton.initialState());

      this.initialStateEdgeTree = automaton.edgeTree(this.automaton.initialState());
      var reachableStatesIndices = new HashSet<Integer>();

      for (Set<Edge<S>> edges : initialStateEdgeTree.values()) {
        if (edges.isEmpty()) {
          reachableStatesIndices.add(REJECTING);
        } else {
          reachableStatesIndices.add(index(Iterables.getOnlyElement(edges).successor()));
        }
      }

      this.initialStateSuccessors = Set.of(reachableStatesIndices.toArray(Integer[]::new));

      this.canonicalAcceptingState = canonicalAcceptingState;
      this.canonicalRejectingState = canonicalRejectingState;

      assert this.canonicalAcceptingState == null
        || this.sinkDetection.apply(this.canonicalAcceptingState).orElseThrow() == ACCEPTING;

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

      return new DeterministicAutomatonWrapper<>(
        automaton,
        Acceptance.fromOmegaAcceptance(automaton.acceptance()),
        null,
        sinkDetection,
        Function.identity(),
        x -> 0.5d,
        uncontrollableApSize,
        null,
        null
      );
    }

    static DeterministicAutomatonWrapper<?, ?> of(LabelledFormula formula) {
      var nnfFormula = SyntacticFragment.NNF.contains(formula) ? formula : formula.nnf();

      if (SyntacticFragments.isSafety(nnfFormula.formula())) {
        var automaton
          = DeterministicConstructionsPortfolio.safety(nnfFormula);
        var factory = automaton.initialState().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          SAFETY, AllAcceptance.class,
          x -> x.isTrue() ? OptionalInt.of(ACCEPTING) : OptionalInt.empty(),
          Function.identity(),
          edge -> edge.successor().trueness(),
          -1,
          factory.of(true),
          factory.of(false)
        );
      }

      if (SyntacticFragments.isCoSafety(nnfFormula.formula())) {
        var automaton
          = DeterministicConstructionsPortfolio.coSafety(nnfFormula);
        var factory = automaton.initialState().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          Acceptance.CO_SAFETY, BuchiAcceptance.class,
          x -> x.isTrue() ? OptionalInt.of(ACCEPTING) : OptionalInt.empty(),
          Function.identity(),
          edge -> edge.successor().trueness(),
          -1,
          factory.of(true),
          factory.of(false)
        );
      }

      var formulasConj = nnfFormula.formula() instanceof Conjunction
        ? nnfFormula.formula().operands
        : Set.of(nnfFormula.formula());

      if (formulasConj.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.gfCoSafety(nnfFormula, false),
          BUCHI, GeneralizedBuchiAcceptance.class,
          x -> OptionalInt.empty(),
          x -> nnfFormula,
          x -> x.colours().contains(0) ? 1.0d : 0.5d,
          -1,
          null,
          null
        );
      }

      var formulasDisj = nnfFormula.formula() instanceof Disjunction
        ? nnfFormula.formula().operands
        : Set.of(nnfFormula.formula());

      if (formulasDisj.stream().allMatch(SyntacticFragments::isFgSafety)) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.fgSafety(nnfFormula, false),
          CO_BUCHI, GeneralizedCoBuchiAcceptance.class,
          x -> OptionalInt.empty(),
          x -> nnfFormula,
          x -> x.colours().contains(0) ? 0.0d : 0.5d,
          -1,
          null,
          null
        );
      }

      if (SyntacticFragments.isSafetyCoSafety(nnfFormula.formula())) {
        var automaton
          = DeterministicConstructionsPortfolio.safetyCoSafety(nnfFormula);
        var factory = automaton.initialState().all().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          BUCHI, BuchiAcceptance.class,
          x -> x.all().isTrue() && x.rejecting().isTrue()
            ? OptionalInt.of(ACCEPTING)
            : OptionalInt.empty(),
          BreakpointStateRejecting::all,
          x -> x.colours().contains(0) ? 1.0d : x.successor().rejecting().trueness(),
          -1,
          BreakpointStateRejecting.of(factory.of(true), factory.of(true)),
          BreakpointStateRejecting.of(factory.of(false), factory.of(false))
        );
      }

      if (SyntacticFragments.isCoSafetySafety(nnfFormula.formula())) {
        var automaton
          = DeterministicConstructionsPortfolio.coSafetySafety(nnfFormula);
        var factory = automaton.initialState().all().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          CO_BUCHI, CoBuchiAcceptance.class,
          x -> x.all().isTrue() ? OptionalInt.of(ACCEPTING) : OptionalInt.empty(),
          BreakpointStateAccepting::all,
          x -> x.colours().contains(0) ? 0.0d : x.successor().accepting().trueness(),
          -1,
          BreakpointStateAccepting.of(factory.of(true), factory.of(true)),
          BreakpointStateAccepting.of(factory.of(false), factory.of(false))
        );
      }

      var function = new LTL2DPAFunction(
          EnumSet.of(COMPLEMENT_CONSTRUCTION_HEURISTIC));
      Automaton<? extends AnnotatedState<EquivalenceClass>, ParityAcceptance> automaton =
        (Automaton) function.apply(nnfFormula);

      if (automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_ODD) {
        return new DeterministicAutomatonWrapper<>(
          automaton,
          PARITY_MIN_ODD, ParityAcceptance.class,
          x -> x.state().isTrue() ? OptionalInt.of(ACCEPTING) : OptionalInt.empty(),
          AnnotatedState::state,
          x -> x.successor().state().trueness(),
          -1,
          null,
          null
        );
      } else {
        assert automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_EVEN;
        return new DeterministicAutomatonWrapper<>(
          automaton,
          PARITY_MIN_EVEN, ParityAcceptance.class,
          x -> x.state().isFalse() ? OptionalInt.of(ACCEPTING) : OptionalInt.empty(),
          AnnotatedState::state,
          x -> 1 - x.successor().state().trueness(),
          -1,
          null,
          null
        );
      }
    }

    int normalise(int stateIndex) {
      if (stateIndex == ACCEPTING) {
        return ACCEPTING;
      }

      if (stateIndex == REJECTING) {
        return REJECTING;
      }

      // TODO: better caching...
      T canonicalObject = this.canonicalizer.apply(index2StateMap.get(stateIndex));
      return canonicalObjectId.computeIfAbsent(canonicalObject, x -> canonicalObjectId.size());
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

    private static BddSet deserialise(CIntPointer tree, int length, BddSetFactory factory) {
      var cache = new BddSet[length / 3];

      assert 3 * cache.length == length;

      // Scan backwards;
      for (int i = cache.length - 1; i >= 0; i--) {
        int atomicProposition = tree.read(3 * i);
        int falseChild = tree.read(3 * i + 1);
        int trueChild = tree.read(3 * i + 2);

        BddSet falseChildSet = falseChild >= 0
          ? cache[falseChild / 3]
          : (falseChild == REJECTING ? factory.of(false) : factory.of(true));

        BddSet trueChildSet = trueChild >= 0
          ? cache[trueChild / 3]
          : (trueChild == REJECTING ? factory.of(false) : factory.of(true));

        BddSet trueBranch = factory.of(atomicProposition);
        BddSet falseBranch = trueBranch.complement();

        cache[i] = (trueBranch.intersection(trueChildSet))
          .union(falseBranch.intersection(falseChildSet));
      }

      return cache[0];
    }

    SerialisedEdgeTree edgeTree(int stateIndex, boolean computeScores) {

      // Load installed global filter.
      var filter = this.filter;

      // If the automaton accepts everything, then index2stateMap is empty.
      S state = stateIndex == INITIAL && index2StateMap.isEmpty()
        ? automaton.initialState()
        : index2StateMap.get(stateIndex);

      MtBdd<Edge<S>> edgeTree;

      if (filter == null) {

        edgeTree = stateIndex == 0 && initialStateEdgeTree != null
          ? initialStateEdgeTree
          : automaton.edgeTree(state);

      } else {

        edgeTree = filter.intersection(
          stateIndex == 0 && initialStateEdgeTree != null
            ? initialStateEdgeTree
            : automaton.edgeTree(state));

      }

      var serialisedEdgeTree = new SerialisedEdgeTree(computeScores);
      serialise(edgeTree, serialisedEdgeTree, -1, new HashMap<>());
      return serialisedEdgeTree;
    }

    boolean edgeTreePrecomputed(int stateIndex) {
      if (automaton instanceof AbstractMemoizingAutomaton) {
        // If the automaton accepts everything, then index2stateMap is empty.
        S state = stateIndex == INITIAL && index2StateMap.isEmpty()
          ? automaton.initialState()
          : index2StateMap.get(stateIndex);

        @SuppressWarnings("unchecked")
        boolean value = ((AbstractMemoizingAutomaton<S, ?>) automaton).edgeTreePrecomputed(state);
        return value;
      }

      return false;
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
