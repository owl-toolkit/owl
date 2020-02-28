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
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_HEURISTIC;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import jhoafparser.parser.generated.ParseException;
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
import owl.cinterface.wrappers.CheckedCDoubleBuffer;
import owl.cinterface.wrappers.CheckedCIntBuffer;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.translations.canonical.DeterministicConstructions;
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
      return CInterface.ENVIRONMENT.factorySupplier().getValuationSetFactory(uncontrollableAp);
    };

    var automaton = DeterministicAutomatonWrapper.of(
      HoaReader.read(hoa, factoryFunction), uncontrollableApSize.get());
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

    return get(cDeterministicAutomaton).automaton.factory().alphabet().size();
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
      get(cDeterministicAutomaton).automaton.factory().alphabet().get(index), buffer, bufferSize);
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
    name = NAMESPACE + "edge_tree_max_capacity_buffer",
    documentation = {
      "Compute the maximal required buffer capacity for the edge_tree() call.",
      "Returns Integer.MAX_VALUE if there is an integer overflow computing the capacity."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnVoid.class
  )
  public static void edgeTreeMaxCapacityBuffer(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton,
    CIntPointer treeBufferCapacity,
    CIntPointer edgeBufferCapacity,
    CIntPointer scoreBufferCapacity) {

    try {

      int height = get(cDeterministicAutomaton).automaton.factory().alphabet().size();

      int nodes = BigInteger.TWO.pow(height + 1).subtract(BigInteger.ONE).intValueExact();
      int leaves = BigInteger.TWO.pow(height).intValueExact();

      treeBufferCapacity.write(Math.multiplyExact(3, nodes));
      edgeBufferCapacity.write(Math.multiplyExact(2, leaves));
      scoreBufferCapacity.write(leaves);

    } catch (ArithmeticException exception) {
      treeBufferCapacity.write(Integer.MAX_VALUE);
      edgeBufferCapacity.write(Integer.MAX_VALUE);
      scoreBufferCapacity.write(Integer.MAX_VALUE);
    }
  }

  @CEntryPoint(
    name = NAMESPACE + "edge_tree",
    documentation = {
      "Serialise the edges leaving the given state into a tree buffer, edge buffer, and an ",
      "optional score buffer. If the scores are not required, the pointer may be set to NULL.",
      "If the buffers are too small the method returns fall.",
      "After the call for all three buffers the position value is updated accordingly.",
      "If the tree buffer position is positive then the tree is interpreted as a mask for ",
      "constructing the returned automaton. Encoding of tree is ",
      "[atomicProposition, falseChild, trueChild, atomicProposition, falseChild, trueChild, ...]",
      "If a child is set to REJECTING (-1) the language of that node is assumed as empty.",
      "If a child is set to ACCEPTING (-2) the language of that node is to be the universe.",
      "The position field must be a a multiple of 3."
    },
    exceptionHandler = CInterface.PrintStackTraceAndExit.ReturnInt.class
  )
  public static boolean edgeTree(
    IsolateThread thread,
    ObjectHandle cDeterministicAutomaton,
    int state,
    CIntBuffer cTreeBuffer,
    CIntBuffer cEdgeBuffer,
    CDoubleBuffer cScoreBuffer) {

    try {
      get(cDeterministicAutomaton).edgeTree(state, cTreeBuffer, cEdgeBuffer, cScoreBuffer);
      return true;
    } catch (IndexOutOfBoundsException exception) {
      // Buffers are too small.
      return false;
    }
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
      return this == BUCHI || this == CO_BUCHI || this == CO_SAFETY || this == SAFETY
        || this == WEAK || this == BOTTOM;
    }

    public boolean isLessOrEqualWeak() {
      return this == CO_SAFETY || this == SAFETY || this == WEAK || this == BOTTOM;
    }
  }

  public static final class DeterministicAutomatonWrapper<S, T> {

    static final int ACCEPTING = -2;
    static final int REJECTING = -1;
    static final int UNKNOWN = Integer.MIN_VALUE;

    public final Automaton<S, ?> automaton;
    public final Acceptance acceptance;

    private final Predicate<S> acceptingSink;
    private final List<S> index2StateMap;
    private final Object2IntMap<S> state2indexMap;
    private final ToDoubleFunction<Edge<S>> qualityScore;
    private final Function<S, T> canonicalizer;
    private final Object2IntMap<T> canonicalObjectId;
    private final int uncontrollableApSize;

    @SuppressWarnings("PMD.UnusedFormalParameter")
    private <A extends OmegaAcceptance> DeterministicAutomatonWrapper(Automaton<S, A> automaton,
      Acceptance acceptance,
      Class<A> acceptanceClassBound,
      Predicate<S> acceptingSink,
      Function<S, T> canonicalizer,
      ToDoubleFunction<Edge<S>> qualityScore,
      int uncontrollableApSize) {
      checkArgument(automaton.initialStates().size() == 1);
      checkArgument(acceptanceClassBound.isInstance(automaton.acceptance()));
      assert automaton.is(Automaton.Property.DETERMINISTIC);

      this.automaton = automaton;
      this.acceptance = acceptance;
      this.acceptingSink = acceptingSink;
      this.qualityScore = qualityScore;

      index2StateMap = new ArrayList<>();
      index2StateMap.add(this.automaton.onlyInitialState());

      state2indexMap = new Object2IntOpenHashMap<>();
      state2indexMap.put(this.automaton.onlyInitialState(), 0);
      state2indexMap.defaultReturnValue(UNKNOWN);

      canonicalObjectId = new Object2IntOpenHashMap<>();
      canonicalObjectId.defaultReturnValue(UNKNOWN);

      this.canonicalizer = canonicalizer;
      this.uncontrollableApSize = uncontrollableApSize;
    }

    @SuppressWarnings("checkstyle")
    static <S, A extends OmegaAcceptance> DeterministicAutomatonWrapper<S, ?>
      of(Automaton<S, A> automaton, int uncontrollableApSize) {
      return new DeterministicAutomatonWrapper<S, S>(
        automaton,
        Acceptance.fromOmegaAcceptance(automaton.acceptance()),
        (Class) automaton.acceptance().getClass(),
        x -> false,
        Function.identity(),
        x -> 0.5d,
        uncontrollableApSize
      );
    }

    @SuppressWarnings("checkstyle")
    static DeterministicAutomatonWrapper<?, ?> of(LabelledFormula formula) {
      if (SyntacticFragments.isSafety(formula.formula())) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.safety(CInterface.ENVIRONMENT, formula),
          SAFETY, AllAcceptance.class,
          EquivalenceClass::isTrue,
          Function.identity(),
          edge -> edge.successor().trueness(),
          -1
        );
      }

      if (SyntacticFragments.isCoSafety(formula.formula())) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.coSafety(CInterface.ENVIRONMENT, formula),
          Acceptance.CO_SAFETY, BuchiAcceptance.class,
          EquivalenceClass::isTrue,
          Function.identity(),
          edge -> edge.successor().trueness(),
          -1
        );
      }

      var formulasConj = formula.formula() instanceof Conjunction
        ? formula.formula().operands
        : Set.of(formula.formula());

      if (formulasConj.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.gfCoSafety(CInterface.ENVIRONMENT, formula, false),
          BUCHI, GeneralizedBuchiAcceptance.class,
          x -> false,
          x -> formula,
          x -> x.inSet(0) ? 1.0d : 0.5d,
          -1
        );
      }

      var formulasDisj = formula.formula() instanceof Disjunction
        ? formula.formula().operands
        : Set.of(formula.formula());

      if (formulasDisj.stream().allMatch(SyntacticFragments::isFgSafety)) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.fgSafety(CInterface.ENVIRONMENT, formula, false),
          CO_BUCHI, GeneralizedCoBuchiAcceptance.class,
          x -> false,
          x -> formula,
          x -> x.inSet(0) ? 0.0d : 0.5d,
          -1
        );
      }

      if (SyntacticFragments.isSafetyCoSafety(formula.formula())) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.safetyCoSafety(CInterface.ENVIRONMENT, formula),
          BUCHI, BuchiAcceptance.class,
          x -> x.all().isFalse() && x.all().isFalse(),
          DeterministicConstructions.BreakpointStateRejecting::all,
          x -> x.inSet(0) ? 1.0d : x.successor().rejecting().trueness(),
          -1
        );
      }

      if (SyntacticFragments.isCoSafetySafety(formula.formula())) {
        return new DeterministicAutomatonWrapper<>(
          DeterministicConstructionsPortfolio.coSafetySafety(CInterface.ENVIRONMENT, formula),
          CO_BUCHI, CoBuchiAcceptance.class,
          x -> x.all().isTrue() && x.accepting().isTrue(),
          DeterministicConstructions.BreakpointStateAccepting::all,
          x -> x.inSet(0) ? 0.0d : x.successor().accepting().trueness(),
          -1
        );
      }

      var function = new LTL2DPAFunction(CInterface.ENVIRONMENT,
        EnumSet.of(COMPLEMENT_CONSTRUCTION_HEURISTIC));
      Automaton<AnnotatedState<EquivalenceClass>, ParityAcceptance> automaton =
        (Automaton) function.apply(formula);

      if (automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_ODD) {
        return new DeterministicAutomatonWrapper<>(
          automaton,
          PARITY_MIN_ODD, ParityAcceptance.class,
          x -> x.state().isTrue(),
          AnnotatedState::state,
          x -> x.successor().state().trueness(),
          -1
        );
      } else {
        assert automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_EVEN;
        return new DeterministicAutomatonWrapper<>(
          automaton,
          PARITY_MIN_EVEN, ParityAcceptance.class,
          x -> x.state().isFalse(),
          AnnotatedState::state,
          x -> 1 - x.successor().state().trueness(),
          -1
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
      CheckedCIntBuffer treeBuffer,
      CheckedCIntBuffer edgeBuffer,
      @Nullable CheckedCDoubleBuffer scoreBuffer,
      int treeBufferWriteBackPosition,
      Object2IntMap<ValuationTree<Edge<S>>> cachedPositions) {

      int cachedPosition = cachedPositions.getInt(edgeTree);

      if (cachedPosition == Integer.MIN_VALUE) {

        if (edgeTree instanceof ValuationTree.Node) {
          var node = (ValuationTree.Node<Edge<S>>) edgeTree;

          cachedPosition = treeBuffer.position();
          treeBuffer.put(cachedPosition, node.variable);
          treeBuffer.position(cachedPosition + 3);

          serialise(node.falseChild, treeBuffer,
            edgeBuffer, scoreBuffer, cachedPosition + 1, cachedPositions);
          serialise(node.trueChild, treeBuffer,
            edgeBuffer, scoreBuffer, cachedPosition + 2, cachedPositions);
        } else {
          var edge = Iterables.getOnlyElement(((ValuationTree.Leaf<Edge<S>>) edgeTree).value, null);

          cachedPosition = -((edgeBuffer.position() / 2) + 1);
          edgeBuffer.put(edge == null ? REJECTING : index(edge.successor()));
          edgeBuffer.put(edge == null ? REJECTING : edge.largestAcceptanceSet());

          if (scoreBuffer != null) {
            scoreBuffer.put(edge == null ? 0.0 : qualityScore.applyAsDouble(edge));
          }
        }

        cachedPositions.put(edgeTree, cachedPosition);
      }

      if (treeBufferWriteBackPosition >= 0) {
        treeBuffer.put(treeBufferWriteBackPosition, cachedPosition);
      }
    }

    private ValuationSet deserialise(CheckedCIntBuffer tree) {
      var cache = new ValuationSet[tree.position() / 3];
      var factory = automaton.factory();

      assert 3 * cache.length == tree.position();

      // Scan backwards;
      for (int i = cache.length - 1; i >= 0; i--) {
        int atomicProposition = tree.get(3 * i);
        int falseChild = tree.get(3 * i + 1);
        int trueChild = tree.get(3 * i + 2);

        ValuationSet falseChildSet = falseChild >= 0
          ? cache[falseChild / 3]
          : (falseChild == REJECTING ? factory.empty() : factory.universe());

        ValuationSet trueChildSet = trueChild >= 0
          ? cache[trueChild / 3]
          : (trueChild == REJECTING ? factory.empty() : factory.universe());

        ValuationSet trueBranch = factory.of(atomicProposition);
        ValuationSet falseBranch = trueBranch.complement();

        cache[i] = (trueBranch.intersection(trueChildSet))
          .union(falseBranch.intersection(falseChildSet));
      }

      return cache[0];
    }

    void edgeTree(int stateIndex,
      CIntBuffer cTreeBuffer, CIntBuffer cEdgeBuffer, CDoubleBuffer cScoreBuffer) {

      var treeBuffer = new CheckedCIntBuffer(cTreeBuffer.buffer(), cTreeBuffer.capacity());
      var edgeBuffer = new CheckedCIntBuffer(cEdgeBuffer.buffer(), cEdgeBuffer.capacity());
      var scoreBuffer = cScoreBuffer.isNull()
        ? null
        : new CheckedCDoubleBuffer(cScoreBuffer.buffer(), cScoreBuffer.capacity());

      S state = index2StateMap.get(stateIndex);
      ValuationTree<Edge<S>> tree;

      if (cTreeBuffer.position() > 0) {
        var treeBufferSlice = treeBuffer.slice(0, cTreeBuffer.position());
        treeBufferSlice.position(cTreeBuffer.position());

        if (automaton.preferredEdgeAccess().get(0) != Automaton.PreferredEdgeAccess.EDGES) {
          System.err.println("Masking might not be needed here."); // NOPMD
        }

        Map<Edge<S>, ValuationSet> labelledEdges = new HashMap<>();

        deserialise(treeBufferSlice).forEach(valuation -> {
          var edge = automaton.edge(state, valuation);

          if (edge != null) {
            labelledEdges.merge(edge, automaton.factory().of(valuation), ValuationSet::union);
          }
        });

        tree = automaton.factory().inverse(labelledEdges);
      } else {
        tree = automaton.edgeTree(state);
      }

      var cachedPositions = new Object2IntOpenHashMap<ValuationTree<Edge<S>>>();
      cachedPositions.defaultReturnValue(Integer.MIN_VALUE);

      treeBuffer.position(0);
      edgeBuffer.position(0);

      serialise(tree, treeBuffer, edgeBuffer, scoreBuffer, -1, cachedPositions);

      cTreeBuffer.position(treeBuffer.position());
      cEdgeBuffer.position(edgeBuffer.position());

      if (scoreBuffer != null) {
        cScoreBuffer.position(scoreBuffer.position());
      }
    }
  }
}
