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
import de.tum.in.naturals.bitset.BitSets;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
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
import owl.automaton.AnnotatedState;
import owl.automaton.Automaton;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedCoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.edge.Edge;
import owl.automaton.hoa.HoaReader;
import owl.bdd.FactorySupplier;
import owl.bdd.ValuationSet;
import owl.bdd.ValuationSetFactory;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateAccepting;
import owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.ltl2dpa.LTL2DPAFunction;

@CContext(CInterface.CDirectives.class)
public final class CAutomaton {

  private static final String NAMESPACE = "automaton_";

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

    var fieldName = "controllable-AP: ";
    int controllableAPStringIndex = hoa.indexOf(fieldName);
    BitSet controllableAPIndices = new BitSet();

    if (controllableAPStringIndex >= 0) {
      String begin = hoa.substring(controllableAPStringIndex + fieldName.length());
      String indices = begin.substring(0, begin.indexOf('\n'));

      for (String index : indices.split("\\s+")) {
        controllableAPIndices.set(Integer.parseInt(index));
      }
    }

    AtomicInteger uncontrollableApSize = new AtomicInteger(-1);

    Function<List<String>, ValuationSetFactory> factoryFunction = (atomicPropositions) -> {
      List<String> uncontrollableAp = new ArrayList<>();
      List<String> controllableAp = new ArrayList<>();

      for (int i = 0, s = atomicPropositions.size(); i < s; i++) {
        if (controllableAPIndices.get(i)) {
          controllableAp.add(atomicPropositions.get(i));
        } else {
          uncontrollableAp.add(atomicPropositions.get(i));
        }
      }

      uncontrollableApSize.set(uncontrollableAp.size());
      uncontrollableAp.addAll(controllableAp);
      return FactorySupplier.defaultSupplier().getValuationSetFactory(uncontrollableAp);
    };

    var automaton = DeterministicAutomatonWrapper.of(
      HoaReader.read(hoa, factoryFunction), uncontrollableApSize.get());
    return ObjectHandles.getGlobal().create(automaton);
  }

  @CEntryPoint(
    name = NAMESPACE + "of",
    documentation = {
      "Translate the given formula to deterministic parity automaton. For greater performance it ",
      "is recommended to use the decomposed DPA construction and reassemble the DPA later.",
      CInterface.CALL_DESTROY
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnObjectHandle.class
  )
  public static ObjectHandle of(
    IsolateThread thread,
    ObjectHandle cLabelledFormula) {

    var formula = ObjectHandles.getGlobal().<LabelledFormula>get(cLabelledFormula);
    var automaton = DeterministicAutomatonWrapper.of(formula);
    return ObjectHandles.getGlobal().create(automaton);
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

    return get(cDeterministicAutomaton).automaton.factory().atomicPropositions().size();
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
      get(cDeterministicAutomaton).automaton.factory().atomicPropositions().get(index), buffer, bufferSize);
  }

  @CEntryPoint(
    name = NAMESPACE + "edge_tree_masking",
    documentation = {
      "Return true if edge masking could speed up computation."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static boolean edgeTreeMasking(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton) {

    return get(cDeterministicAutomaton).automaton.preferredEdgeAccess().get(0)
      == Automaton.PreferredEdgeAccess.EDGES;
  }

  @CEntryPoint(
    name = NAMESPACE + "edge_tree",
    documentation = {
      "Serialise the edges leaving the given state into a tree buffer, edge buffer, and an ",
      "optional score buffer. If the scores are not required, the pointer may be set to NULL.",
      "The pointer returned via the sized_{int,double}_array_t structures must be freed using",
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
          featuresBuilder.add(automaton.state2indexMap.getInt(state));
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

    public static Acceptance fromOmegaAcceptance(OmegaAcceptance acceptance) {
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

    // Internal constants
    private static final int UNKNOWN = Integer.MIN_VALUE;

    final Automaton<S, ?> automaton;
    final Acceptance acceptance;
    final int uncontrollableApSize;

    // Mapping information
    private final Predicate<? super S> acceptingSink;
    private final List<S> index2StateMap;
    private final Object2IntMap<S> state2indexMap;

    // Additional features for C interface
    private final ToDoubleFunction<? super Edge<S>> qualityScore;
    private final Function<? super S, ? extends T> canonicalizer;
    private final Object2IntMap<T> canonicalObjectId;

    // Initial state caching.
    @Nullable
    final ValuationTree<Edge<S>> initialStateEdgeTree;
    @Nullable
    final Set<Integer> initialStateSuccessors;

    @Nullable
    ValuationSet filter;

    @Nullable
    final S canonicalAcceptingState;

    @Nullable
    final S canonicalRejectingState;

    private <A extends OmegaAcceptance> DeterministicAutomatonWrapper(
      Automaton<S, A> automaton,
      Acceptance acceptance,
      Class<A> acceptanceClassBound,
      Predicate<? super S> acceptingSink,
      Function<? super S, ? extends T> canonicalizer,
      ToDoubleFunction<? super Edge<S>> qualityScore,
      int uncontrollableApSize,
      @Nullable S canonicalAcceptingState,
      @Nullable S canonicalRejectingState) {

      checkArgument(automaton.initialStates().size() == 1);
      checkArgument(acceptanceClassBound.isInstance(automaton.acceptance()));
      assert automaton.is(Automaton.Property.DETERMINISTIC);

      this.automaton = automaton;
      this.acceptance = acceptance;
      this.acceptingSink = acceptingSink;
      this.qualityScore = qualityScore;

      this.index2StateMap = new ArrayList<>();
      this.index2StateMap.add(this.automaton.onlyInitialState());

      this.state2indexMap = new Object2IntOpenHashMap<>();
      this.state2indexMap.put(this.automaton.onlyInitialState(), INITIAL);
      this.state2indexMap.defaultReturnValue(UNKNOWN);

      this.canonicalObjectId = new Object2IntOpenHashMap<>();
      this.canonicalObjectId.defaultReturnValue(UNKNOWN);

      this.canonicalizer = canonicalizer;
      this.uncontrollableApSize = uncontrollableApSize;

      if (automaton.preferredEdgeAccess().get(0) == Automaton.PreferredEdgeAccess.EDGE_TREE) {
        this.initialStateEdgeTree = automaton.edgeTree(this.automaton.onlyInitialState());

        var reachableStatesIndices = new HashSet<Integer>();

        for (Set<Edge<S>> edges : initialStateEdgeTree.values()) {
          if (edges.isEmpty()) {
            reachableStatesIndices.add(REJECTING);
          } else {
            reachableStatesIndices.add(index(Iterables.getOnlyElement(edges).successor()));
          }
        }

        this.initialStateSuccessors = Set.of(reachableStatesIndices.toArray(Integer[]::new));
      } else {
        this.initialStateEdgeTree = null;
        this.initialStateSuccessors = null;
      }

      this.canonicalAcceptingState = canonicalAcceptingState;
      this.canonicalRejectingState = canonicalRejectingState;

      assert this.canonicalAcceptingState == null
        || this.acceptingSink.test(this.canonicalAcceptingState);

      if (ImageInfo.inImageCode()) {
        boolean consistentDeclarations = INITIAL == CInterface.owlInitialState()
          && ACCEPTING == CInterface.owlAcceptingSink()
          && REJECTING == CInterface.owlRejectingSink();

        if (!consistentDeclarations) {
          throw new AssertionError("C headers declare conflicting constants.");
        }
      }
    }

    static <S, A extends OmegaAcceptance> DeterministicAutomatonWrapper<S, ?>
      of(Automaton<S, A> automaton, int uncontrollableApSize) {
      return new DeterministicAutomatonWrapper<S, S>(
        automaton,
        Acceptance.fromOmegaAcceptance(automaton.acceptance()),
        (Class) automaton.acceptance().getClass(),
        x -> false,
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
        var factory = automaton.onlyInitialState().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          SAFETY, AllAcceptance.class,
          EquivalenceClass::isTrue,
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
        var factory = automaton.onlyInitialState().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          Acceptance.CO_SAFETY, BuchiAcceptance.class,
          EquivalenceClass::isTrue,
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
          x -> false,
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
          x -> false,
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
        var factory = automaton.onlyInitialState().all().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          BUCHI, BuchiAcceptance.class,
          x -> x.all().isTrue() && x.rejecting().isTrue(),
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
        var factory = automaton.onlyInitialState().all().factory();

        return new DeterministicAutomatonWrapper<>(
          automaton,
          CO_BUCHI, CoBuchiAcceptance.class,
          x -> x.all().isTrue(),
          BreakpointStateAccepting::all,
          x -> x.colours().contains(0) ? 0.0d : x.successor().accepting().trueness(),
          -1,
          BreakpointStateAccepting.of(factory.of(true), factory.of(true)),
          BreakpointStateAccepting.of(factory.of(false), factory.of(false))
        );
      }

      var function = new LTL2DPAFunction(
          EnumSet.of(COMPLEMENT_CONSTRUCTION_HEURISTIC));
      Automaton<AnnotatedState<EquivalenceClass>, ParityAcceptance> automaton =
        (Automaton) function.apply(nnfFormula);

      if (automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_ODD) {
        return new DeterministicAutomatonWrapper<>(
          automaton,
          PARITY_MIN_ODD, ParityAcceptance.class,
          x -> x.state().isTrue(),
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
          x -> x.state().isFalse(),
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
      return canonicalObjectId.computeIntIfAbsent(canonicalObject, x -> canonicalObjectId.size());
    }

    private int index(@Nullable S state) {
      if (state == null) {
        return REJECTING;
      }

      if (acceptingSink.test(state)) {
        return ACCEPTING;
      }

      int index = state2indexMap.getInt(state);

      if (index == UNKNOWN) {
        index2StateMap.add(state);
        state2indexMap.put(state, index2StateMap.size() - 1);
        index = index2StateMap.size() - 1;
      }

      return index;
    }

    private void serialise(
      ValuationTree<Edge<S>> edgeTree,
      SerialisedEdgeTree buffers,
      int treeBufferWriteBackPosition,
      Object2IntMap<ValuationTree<Edge<S>>> cachedPositions) {

      var treeBuffer = buffers.tree;
      int position = cachedPositions.getInt(edgeTree);

      if (position == Integer.MIN_VALUE) {

        if (edgeTree instanceof ValuationTree.Node) {
          var node = (ValuationTree.Node<Edge<S>>) edgeTree;

          position = treeBuffer.size();
          treeBuffer.add(node.variable, -1, -1);

          serialise(node.falseChild, buffers, position + 1, cachedPositions);
          serialise(node.trueChild, buffers, position + 2, cachedPositions);
        } else {
          var edge = Iterables.getOnlyElement(((ValuationTree.Leaf<Edge<S>>) edgeTree).value, null);

          var edgeBuffer = buffers.edges;

          position = -((edgeBuffer.size() / 2) + 1);

          if (edge == null) {
            edgeBuffer.add(REJECTING, REJECTING);
          } else {
            edgeBuffer.add(index(edge.successor()), edge.largestAcceptanceSet());
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

    private static ValuationSet deserialise(CIntPointer tree, int length, ValuationSetFactory factory) {
      var cache = new ValuationSet[length / 3];

      assert 3 * cache.length == length;

      // Scan backwards;
      for (int i = cache.length - 1; i >= 0; i--) {
        int atomicProposition = tree.read(3 * i);
        int falseChild = tree.read(3 * i + 1);
        int trueChild = tree.read(3 * i + 2);

        ValuationSet falseChildSet = falseChild >= 0
          ? cache[falseChild / 3]
          : (falseChild == REJECTING ? factory.of() : factory.universe());

        ValuationSet trueChildSet = trueChild >= 0
          ? cache[trueChild / 3]
          : (trueChild == REJECTING ? factory.of() : factory.universe());

        ValuationSet trueBranch = factory.of(atomicProposition);
        ValuationSet falseBranch = trueBranch.complement();

        cache[i] = (trueBranch.intersection(trueChildSet))
          .union(falseBranch.intersection(falseChildSet));
      }

      return cache[0];
    }

    SerialisedEdgeTree edgeTree(int stateIndex, boolean computeScores) {

      // Load installed global filter.
      var filter = this.filter;
      S state = index2StateMap.get(stateIndex);
      ValuationTree<Edge<S>> edgeTree;

      if (filter == null) {

        edgeTree = stateIndex == 0 && initialStateEdgeTree != null
          ? initialStateEdgeTree
          : automaton.edgeTree(state);

      } else if (automaton.preferredEdgeAccess().get(0) == Automaton.PreferredEdgeAccess.EDGES) {

        assert initialStateEdgeTree == null;

        var factory = automaton.factory();
        var labelledEdges = new HashMap<Edge<S>, ValuationSet>();

        for (BitSet valuation : BitSets.powerSet(factory.atomicPropositions().size())) {
          if (!filter.contains(valuation)) {
            continue;
          }

          var edge = automaton.edge(state, valuation);

          if (edge != null) {
            labelledEdges.merge(edge, factory.of(valuation), ValuationSet::union);
          }
        }

        edgeTree = factory.toValuationTree(labelledEdges);

      } else {

        edgeTree = filter.filter(
          stateIndex == 0 && initialStateEdgeTree != null
            ? initialStateEdgeTree
            : automaton.edgeTree(state));

      }

      var serialisedEdgeTree = new SerialisedEdgeTree(computeScores);
      var cachedPositions = new Object2IntOpenHashMap<ValuationTree<Edge<S>>>();
      cachedPositions.defaultReturnValue(Integer.MIN_VALUE);
      serialise(edgeTree, serialisedEdgeTree, -1, cachedPositions);
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
