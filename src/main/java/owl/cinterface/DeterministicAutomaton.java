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

package owl.cinterface;

import static com.google.common.base.Preconditions.checkArgument;
import static owl.cinterface.Acceptance.BUCHI;
import static owl.cinterface.Acceptance.CO_BUCHI;
import static owl.cinterface.Acceptance.PARITY_MIN_EVEN;
import static owl.cinterface.Acceptance.PARITY_MIN_ODD;
import static owl.cinterface.Acceptance.SAFETY;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_HEURISTIC;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
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
import owl.collections.ValuationTree;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.canonical.DeterministicConstructions;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.util.annotation.CEntryPoint;

// This is a JNI entry point. No touching.
public final class DeterministicAutomaton<S, T> {

  public static final int ACCEPTING = -2;
  public static final int REJECTING = -1;
  private static final int UNKNOWN = Integer.MIN_VALUE;

  private static final Environment ENV = Environment.standard();

  private final Acceptance acceptance;
  private final Predicate<S> acceptingSink;
  private final Automaton<S, ?> automaton;
  private final List<S> index2StateMap;
  private final Object2IntMap<S> state2indexMap;
  private final ToDoubleFunction<Edge<S>> qualityScore;
  private final Function<S, T> canonicalizer;
  private final Object2IntMap<T> canonicalObjectId;

  @SuppressWarnings("PMD.UnusedFormalParameter")
  private <A extends OmegaAcceptance> DeterministicAutomaton(Automaton<S, A> automaton,
    Acceptance acceptance,
    Class<A> acceptanceClassBound,
    Predicate<S> acceptingSink,
    Function<S, T> canonicalizer,
    ToDoubleFunction<Edge<S>> qualityScore) {
    checkArgument(automaton.initialStates().size() == 1);
    checkArgument(acceptanceClassBound.isInstance(automaton.acceptance()));

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
  }

  public static DeterministicAutomaton<?, ?> of(LabelledFormula formula) {
    if (SyntacticFragments.isSafety(formula.formula())) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.safety(ENV, formula),
        SAFETY, AllAcceptance.class,
        EquivalenceClass::isTrue,
        Function.identity(),
        edge -> edge.successor().trueness()
      );
    }

    if (SyntacticFragments.isCoSafety(formula.formula())) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.coSafety(ENV, formula),
        Acceptance.CO_SAFETY, BuchiAcceptance.class,
        EquivalenceClass::isTrue,
        Function.identity(),
        edge -> edge.successor().trueness()
      );
    }

    var formulasConj = formula.formula() instanceof Conjunction
      ? formula.formula().operands
      : Set.of(formula.formula());

    if (formulasConj.stream().allMatch(SyntacticFragments::isGfCoSafety)) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.gfCoSafety(ENV, formula, false),
        BUCHI, GeneralizedBuchiAcceptance.class,
        x -> false,
        x -> formula,
        x -> x.inSet(0) ? 1.0d : 0.5d
      );
    }

    if (SyntacticFragments.isSafetyCoSafety(formula.formula())) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.safetyCoSafety(ENV, formula),
        BUCHI, BuchiAcceptance.class,
        x -> x.all().isFalse() && x.all().isFalse(),
        DeterministicConstructions.BreakpointStateRejecting::all,
        x -> x.inSet(0) ? 1.0d : x.successor().rejecting().trueness()
      );
    }

    var formulasDisj = formula.formula() instanceof Disjunction
      ? formula.formula().operands
      : Set.of(formula.formula());

    if (formulasDisj.stream().allMatch(SyntacticFragments::isFgSafety)) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.fgSafety(ENV, formula, false),
        CO_BUCHI, GeneralizedCoBuchiAcceptance.class,
        x -> false,
        x -> formula,
        x -> x.inSet(0) ? 0.0d : 0.5d
      );
    }

    if (SyntacticFragments.isCoSafetySafety(formula.formula())) {
      return new DeterministicAutomaton<>(
        DeterministicConstructionsPortfolio.coSafetySafety(ENV, formula),
        CO_BUCHI, CoBuchiAcceptance.class,
        x -> x.all().isTrue() && x.accepting().isTrue(),
        DeterministicConstructions.BreakpointStateAccepting::all,
        x -> x.inSet(0) ? 0.0d : x.successor().accepting().trueness()
      );
    }

    var function = new LTL2DPAFunction(ENV, EnumSet.of(COMPLEMENT_CONSTRUCTION_HEURISTIC));
    Automaton<AnnotatedState<EquivalenceClass>, ParityAcceptance> automaton =
      (Automaton) function.apply(formula);

    if (automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_ODD) {
      return new DeterministicAutomaton<>(
        automaton,
        PARITY_MIN_ODD, ParityAcceptance.class,
        x -> x.state().isTrue(),
        AnnotatedState::state,
        x -> x.successor().state().trueness()
      );
    } else {
      assert automaton.acceptance().parity() == ParityAcceptance.Parity.MIN_EVEN;
      return new DeterministicAutomaton<>(
        automaton,
        PARITY_MIN_EVEN, ParityAcceptance.class,
        x -> x.state().isFalse(),
        AnnotatedState::state,
        x -> 1 - x.successor().state().trueness()
      );
    }
  }

  @CEntryPoint
  public int acceptance() {
    return acceptance.ordinal();
  }

  @CEntryPoint
  public int acceptanceSetCount() {
    return automaton.acceptance().acceptanceSets();
  }

  @CEntryPoint
  public int[] edges(int stateIndex) {
    S state = index2StateMap.get(stateIndex);
    IntArrayList nodes = new IntArrayList();
    IntArrayList leaves = new IntArrayList();
    // Reserve space for offset.
    nodes.add(-1);
    serialise(automaton.edgeTree(state), new HashMap<>(), nodes, leaves);
    // Concatenate.
    nodes.set(0, nodes.size());
    nodes.addAll(leaves);
    return nodes.toIntArray();
  }

  @CEntryPoint
  public double qualityScore(int successorIndex, int colour) {
    S successor = index2StateMap.get(successorIndex);
    Edge<S> edge = colour >= 0 ? Edge.of(successor, colour) : Edge.of(successor);
    return qualityScore.applyAsDouble(edge);
  }

  int size() {
    return automaton.size();
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

  private int serialise(ValuationTree<Edge<S>> tree, Map<ValuationTree<Edge<S>>, Integer> cache,
    IntArrayList nodes, IntArrayList leaves) {
    int index = cache.getOrDefault(tree, Integer.MIN_VALUE);

    if (index != Integer.MIN_VALUE) {
      return index;
    }

    if (tree instanceof ValuationTree.Node) {
      var node = (ValuationTree.Node<Edge<S>>) tree;
      index = nodes.size();
      nodes.add(node.variable);
      nodes.add(-1);
      nodes.add(-1);
      nodes.set(index + 1, serialise(node.falseChild, cache, nodes, leaves));
      nodes.set(index + 2, serialise(node.trueChild, cache, nodes, leaves));
    } else {
      var edge = Iterables.getOnlyElement(((ValuationTree.Leaf<Edge<S>>) tree).value, null);
      index = -leaves.size();
      leaves.add(edge == null ? REJECTING : index(edge.successor()));
      leaves.add(edge == null ? REJECTING : edge.largestAcceptanceSet());
    }

    cache.put(tree, index);
    return index;
  }
}
