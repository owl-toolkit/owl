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

package owl.translations.ltl2dela;

import static owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import static owl.translations.canonical.DeterministicConstructions.SafetyCoSafety;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.NOT_SUSPENDABLE;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.SUSPENDABLE;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.TERMINAL_ACCEPTING;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.TERMINAL_REJECTING;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.TRANSIENT_NOT_SUSPENDED;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.TRANSIENT_SUSPENDED;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.WEAK_ACCEPTING_NOT_SUSPENDED;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.WEAK_ACCEPTING_SUSPENDED;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.WEAK_REJECTING_NOT_SUSPENDED;
import static owl.translations.ltl2dela.NormalformDELAConstruction.Classification.WEAK_REJECTING_SUSPENDED;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_PI_2_AND_FG_PI_1;
import static owl.translations.mastertheorem.Normalisation.NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1;

import com.google.auto.value.AutoValue;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.collections.BitSet2;
import owl.collections.Collections3;
import owl.collections.ImmutableBitSet;
import owl.logic.propositional.PropositionalFormula;
import owl.logic.propositional.sat.Solver;
import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.Negation;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.PropositionalSimplifier;
import owl.ltl.rewriter.PushNextThroughPropositionalVisitor;
import owl.ltl.rewriter.SimplifierRepository;
import owl.ltl.visitors.PropositionalVisitor;
import owl.translations.BlockingElements;
import owl.translations.mastertheorem.Normalisation;

/**
 * A translation from LTL to deterministic Emerson-Lei automata using the \Delta_2-normalisation
 * procedure. Future Work / TODO:
 * - Use lookahead to find more accepting and rejecting sinks. For this use an exhaustive SCC
 *   analysis that is bounded by the lookahead.
 * - Round-Robin Chains that are independent from GF- / FG-subformulas. The heuristic depends on the
 *   fact that suspension collapse a SCC to a single state.
 * - Store round-robin chains for beta generation.
 * - Use non-trivial beta generation (using LTL Axioms.)
 */
public final class NormalformDELAConstruction
  implements Function<LabelledFormula, Automaton<NormalformDELAConstruction.State, ?>> {

  private static final Normalisation NORMALISATION
    = Normalisation.of(SE20_SIGMA_2_AND_GF_SIGMA_1, false);

  private static final Normalisation DUAL_NORMALISATION
    = Normalisation.of(SE20_PI_2_AND_FG_PI_1, false);

  private final OptionalInt lookahead;

  public NormalformDELAConstruction(OptionalInt lookahead) {
    this.lookahead = lookahead;
  }

  @Override
  public Automaton<State, ?> apply(LabelledFormula formula) {
    return applyConstruction(formula).automaton();
  }

  public Construction applyConstruction(LabelledFormula formula) {
    // Ensure that the input formula is in negation normal form and that
    // X-operators occur only in-front of temporal operators.
    var normalisedFormula = RemoveNegation.apply(
      PushNextThroughPropositionalVisitor.apply(formula));
    return new Construction(normalisedFormula, lookahead);
  }

  private static final class RemoveNegation extends PropositionalVisitor<Formula> {

    private static final RemoveNegation INSTANCE = new RemoveNegation();

    public static LabelledFormula apply(LabelledFormula labelledFormula) {
      return labelledFormula.wrap(labelledFormula.formula().accept(INSTANCE));
    }

    @Override
    protected Formula visit(Formula.TemporalOperator formula) {
      return formula.nnf();
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      var leftOperand = biconditional.leftOperand().accept(this);
      var rightOperand = biconditional.rightOperand().accept(this);

      if (SyntacticFragments.DELTA_1.contains(leftOperand)
        && SyntacticFragments.DELTA_1.contains(rightOperand)) {

        var nnfFormula = Biconditional.of(leftOperand, rightOperand).nnf();
        assert SyntacticFragments.DELTA_1.contains(nnfFormula);
        return nnfFormula;
      }

      return Biconditional.of(leftOperand, rightOperand);
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Negation negation) {
      return negation.operand().not().accept(this);
    }
  }

  public static class Construction {

    // Underlying DBW.
    private final SafetyCoSafety dbw;
    private final BreakpointStateRejectingClassifier classifier;
    private final EmersonLeiAcceptance acceptance;
    private final ImmutableBitSet roundRobinCandidates;

    // Caches
    private final Map<State, PropositionalFormula<Integer>> alphaCache = new HashMap<>();
    private final Map<State, PropositionalFormula<Integer>> betaCache = new HashMap<>();
    private final Map<PropositionalFormula<Integer>, ImmutableBitSet> notAlphaColoursCache
      = new HashMap<>();

    private final AbstractMemoizingAutomaton<State, EmersonLeiAcceptance> automaton;

    private Construction(LabelledFormula formula, OptionalInt lookahead) {

      var factories
        = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());

      // Underlying automaton.
      dbw = SafetyCoSafety.of(factories, BooleanConstant.TRUE, true, true);

      var normalFormConstructor = new NormalFormConverter();
      var normalForm
        = PropositionalSimplifier.INSTANCE.apply(formula.formula().accept(normalFormConstructor));
      var referenceCounterVisitor = new CountingVisitor();
      referenceCounterVisitor.apply(normalForm);
      referenceCounterVisitor.referenceCounter.entrySet().removeIf(e -> e.getValue().equals(1));
      var initialStateFormulaConstructor
        = new InitialStateConstructor(dbw, referenceCounterVisitor.referenceCounter.keySet());
      var initialStateFormula = initialStateFormulaConstructor.apply(normalForm);
      var initialStates = initialStateFormulaConstructor.initialStates;
      classifier = new BreakpointStateRejectingClassifier(dbw,
        // Scale lookahead for each component.
        lookahead.stream().map(x -> Math.max(0, x / Math.max(1, initialStates.size()))).findAny());
      roundRobinCandidates = ImmutableBitSet
        .copyOf(initialStateFormulaConstructor.roundRobinCandidate);

      // Assert that round-robin candidates are referred to in the state-formula at most once.
      var occurrences = new HashMap<>(initialStateFormula.countVariables());
      occurrences.values().removeIf(x -> x > 1);
      assert occurrences.keySet().containsAll(roundRobinCandidates);

      Map<Integer, BreakpointStateRejecting> stateMap = new HashMap<>(initialStates.size());

      for (int i = 0, s = initialStates.size(); i < s; i++) {
        stateMap.put(i, initialStates.get(i));
      }

      State initialState
        = createState(initialStateFormula, stateMap, ImmutableBitSet.of(), new BitSet());
      acceptance = EmersonLeiAcceptance.of(initialState.stateFormula());

      automaton = new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        dbw.atomicPropositions(), dbw.factory(), Set.of(initialState), acceptance) {

        @Override
        protected MtBdd<Edge<State>> edgeTreeImpl(State state) {
          Map<Integer, BreakpointStateRejecting> stateMap = state.stateMap();
          List<Integer> keys = new ArrayList<>(stateMap.size());
          List<MtBdd<Edge<BreakpointStateRejecting>>> values = new ArrayList<>(stateMap.size());

          stateMap.forEach((key, value) -> {
            keys.add(key);
            values.add(dbw.edgeTree(value));
          });

          // Remember, dbw needs to be complete!
          return edgeTreeImpl(state, keys, values, new HashMap<>(), 0);
        }

        private MtBdd<Edge<State>> edgeTreeImpl(
          State state,
          List<Integer> keys,
          List<MtBdd<Edge<BreakpointStateRejecting>>> values,
          Map<List<Edge<BreakpointStateRejecting>>, Edge<State>> mapperCache,
          int leaves) {

          int variable = nextVariable(values);

          if (variable == Integer.MAX_VALUE) {
            List<Edge<BreakpointStateRejecting>> edges = new ArrayList<>(values.size());

            for (int i = 0, s = keys.size(); i < s; i++) {
              var value = values.get(i);

              if (value instanceof MtBdd.Leaf) {
                edges.add(Iterables.getOnlyElement(
                  ((MtBdd.Leaf<Edge<BreakpointStateRejecting>>) value).value));
              } else {
                assert value == null;
                edges.add(null);
              }
            }

            Edge<State> edge = mapperCache.computeIfAbsent(
              edges, y -> Construction.this.edge(state, keys, edges));

            return edge.successor().stateFormula().isFalse() ? MtBdd.of() : MtBdd.of(Set.of(edge));
          }

          var falseTrees = new ArrayList<>(values);
          falseTrees.replaceAll(x -> descendFalseIf(x, variable));
          int falseLeaves = countLeaves(falseTrees);
          if (falseLeaves > leaves) {
            shortCircuit(state, keys, falseTrees);
          }
          var falseCartesianProduct
            = edgeTreeImpl(state, keys, falseTrees, mapperCache, falseLeaves);

          var trueTrees = new ArrayList<>(values);
          trueTrees.replaceAll(x -> descendTrueIf(x, variable));
          int trueLeaves = countLeaves(trueTrees);
          if (trueLeaves > leaves) {
            shortCircuit(state, keys, trueTrees);
          }
          var trueCartesianProduct
            = edgeTreeImpl(state, keys, trueTrees, mapperCache, trueLeaves);

          return MtBdd.of(variable, trueCartesianProduct, falseCartesianProduct);
        }

        private int nextVariable(Collection<? extends MtBdd<?>> trees) {
          int variable = Integer.MAX_VALUE;

          for (var tree : trees) {
            // Remember tree might be null, but instanceof also checks for that.
            variable = Math.min(variable, tree instanceof MtBdd.Node
              ? ((MtBdd.Node<?>) tree).variable
              : Integer.MAX_VALUE);
          }

          return variable;
        }

        @Nullable
        private <E> MtBdd<E> descendFalseIf(@Nullable MtBdd<E> tree, int variable) {
          if (tree instanceof MtBdd.Node && ((MtBdd.Node<E>) tree).variable == variable) {
            return ((MtBdd.Node<E>) tree).falseChild;
          } else {
            return tree;
          }
        }

        @Nullable
        private <E> MtBdd<E> descendTrueIf(@Nullable MtBdd<E> tree, int variable) {
          if (tree instanceof MtBdd.Node && ((MtBdd.Node<E>) tree).variable == variable) {
            return ((MtBdd.Node<E>) tree).trueChild;
          } else {
            return tree;
          }
        }

        private void shortCircuit(
          State state, List<Integer> keys, List<MtBdd<Edge<BreakpointStateRejecting>>> trees) {

          List<Edge<BreakpointStateRejecting>> leaves = new ArrayList<>(trees.size());

          for (MtBdd<Edge<BreakpointStateRejecting>> x : trees) {
            leaves.add(x instanceof MtBdd.Leaf
              ? Iterables.getOnlyElement(((MtBdd.Leaf<Edge<BreakpointStateRejecting>>) x).value)
              : null);
          }

          var successorMap = successorMap(keys, leaves, null);
          var classification = classifier.classify(successorMap);

          // Keep this in sync with createState
          PropositionalFormula<Integer> newStateFormula = state.stateFormula().substitute(index -> {
            if (!classification.containsKey(index)) {
              return PropositionalFormula.Variable.of(index);
            }

            return switch (classification.get(index)) {
              case TERMINAL_ACCEPTING -> PropositionalFormula.trueConstant();
              case TERMINAL_REJECTING -> PropositionalFormula.falseConstant();
              default -> PropositionalFormula.Variable.of(index);
            };
          });

          BitSet remainingVariables = BitSet2.copyOf(newStateFormula.variables());

          for (int i = 0, s = keys.size(); i < s; i++) {
            if (trees.get(i) instanceof MtBdd.Node && !remainingVariables.get(keys.get(i))) {
              trees.set(i, null);
            }
          }
        }

        private <E> int countLeaves(List<? extends MtBdd<E>> trees) {
          int counter = 0;

          for (MtBdd<E> tree : trees) {
            if (tree instanceof MtBdd.Leaf) {
              counter++;
            }
          }

          return counter;
        }
      };
    }

    public AbstractMemoizingAutomaton<State, EmersonLeiAcceptance> automaton() {
      return automaton;
    }

    private ImmutableBitSet notAlphaColours(PropositionalFormula<Integer> alpha) {
      var acceptanceCondition = automaton.acceptance().booleanExpression();
      var alphaColours = BitSet2.copyOf(alpha.variables());
      var partialAssignment
        = PropositionalFormulaHelper.findPartialAssignment(acceptanceCondition, alpha);

      if (partialAssignment != null) {
        BitSet padding = new BitSet();
        partialAssignment.forEach(padding::set);

        // TODO: Move verification code to findPartialAssignment.
        // Verify correctness.
        var simplifiedAcceptance = acceptanceCondition.substitute(
          x -> alphaColours.get(x)
            ? PropositionalFormula.Variable.of(x)
            : padding.get(x)
              ? PropositionalFormula.trueConstant()
              : PropositionalFormula.falseConstant());

        // Quick, syntactic check
        if (simplifiedAcceptance.equals(alpha)) {
          return ImmutableBitSet.copyOf(padding);
        }

        // Complete, semantic check.
        var xor = PropositionalFormula.Negation.of(
          PropositionalFormula.Biconditional.of(alpha, simplifiedAcceptance));

        if (Solver.model(xor).isEmpty()) {
          return ImmutableBitSet.copyOf(padding);
        }
      }

      throw new AssertionError("This should not have been reached, since alpha is "
        + "computed from acceptanceCondition by substituting variables by constants.");
    }

    private Map<Integer, BreakpointStateRejecting> successorMap(
      List<Integer> keys, List<Edge<BreakpointStateRejecting>> edges, @Nullable BitSet colours) {
      Map<Integer, BreakpointStateRejecting> successorMap = new HashMap<>(edges.size());

      for (int i = 0, s = keys.size(); i < s; i++) {
        var key = keys.get(i);
        var value = edges.get(i);

        if (value == null) {
          continue;
        }

        var oldValue = successorMap.put(key, value.successor());
        assert oldValue == null;

        if (colours != null && value.colours().contains(0)) {
          colours.set(key);
        }
      }

      return successorMap;
    }

    private Edge<State> edge(
      State state, List<Integer> keys, List<Edge<BreakpointStateRejecting>> edges) {

      BitSet colours = new BitSet();
      Map<Integer, BreakpointStateRejecting> successorMap = successorMap(keys, edges, colours);
      State successor = createState(
        state.stateFormula(), successorMap, state.roundRobinCounters(), colours);

      PropositionalFormula<Integer> alpha = alphaCache.get(successor);
      // Remove colours outside of alpha...
      colours.and(BitSet2.copyOf(alpha.variables()));
      // ...and replace them with a fixed colour pattern.
      notAlphaColoursCache.computeIfAbsent(alpha, this::notAlphaColours).copyInto(colours);

      return Edge.of(successor, colours);
    }

    private State createState(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, BreakpointStateRejecting> stateMap,
      ImmutableBitSet oldRoundRobinCounters,
      BitSet acceptingEdges) {

      assert roundRobinCandidates.containsAll(oldRoundRobinCounters);

      NavigableMap<Integer, Classification> classification = classifier.classify(stateMap);

      // Keep this in sync with shortCircuit.
      PropositionalFormula<Integer> newStateFormula
        = stateFormula.substitute(index -> {
          // The state has been short-circuited.
          if (!classification.containsKey(index)) {
            return PropositionalFormula.Variable.of(index);
          }

        return switch (classification.get(index)) {
          case TERMINAL_ACCEPTING -> PropositionalFormula.trueConstant();
          case TERMINAL_REJECTING -> PropositionalFormula.falseConstant();
          default -> PropositionalFormula.Variable.of(index);
        };
        });

      assert stateMap.keySet().containsAll(newStateFormula.variables()) : "Short-circuiting failed";
      newStateFormula = pruneRedundantConjunctsAndDisjuncts(newStateFormula, stateMap);

      {
        var remainingVariables = newStateFormula.variables();
        classification.keySet().retainAll(remainingVariables);
        stateMap.keySet().retainAll(remainingVariables);
      }

      assert !classification.containsValue(TERMINAL_ACCEPTING);
      assert !classification.containsValue(TERMINAL_REJECTING);

      // If there is a state that is transient and can be suspended then suspend everything.
      if (classification.containsValue(TRANSIENT_SUSPENDED)
        || classification.containsValue(TRANSIENT_NOT_SUSPENDED)) {

        // If there is a state that is transient and but cannot be safely suspended, then suspend
        // everything except that state.
        int protectedIndex = classification.containsValue(TRANSIENT_SUSPENDED)
          ? -1
          : classification.entrySet().stream()
            .filter(entry -> entry.getValue() == TRANSIENT_NOT_SUSPENDED)
            .map(Map.Entry::getKey)
            .findFirst().orElseThrow();

        stateMap.entrySet().forEach(entry -> {
          if (entry.getKey() != protectedIndex) {
            entry.setValue(entry.getValue().suspend());
          }
        });

        var state = State.of(newStateFormula, stateMap, ImmutableBitSet.of());
        alphaCache.put(state, PropositionalFormula.trueConstant());
        betaCache.put(state, PropositionalFormula.trueConstant());
        return state;
      }

      assert !classification.containsValue(TRANSIENT_SUSPENDED);
      assert !classification.containsValue(TRANSIENT_NOT_SUSPENDED);

      // Evaluate all suspendable states that belong to a weak accepting and rejecting SCC.
      var alpha = newStateFormula.substitute(
        index -> switch (classification.get(index)) {
          case WEAK_ACCEPTING_SUSPENDED -> PropositionalFormula.trueConstant();
          case WEAK_REJECTING_SUSPENDED -> PropositionalFormula.falseConstant();
          default -> PropositionalFormula.Variable.of(index);
        });

      {
        var remainingVariables = alpha.variables();

        classification.forEach((key, value) -> {
          if (remainingVariables.contains(key)) {
            return;
          }

          stateMap.computeIfPresent(key, (x, y) -> y.suspend());
        });

        classification.keySet().retainAll(remainingVariables);
      }

      assert !classification.containsValue(WEAK_ACCEPTING_SUSPENDED);
      assert !classification.containsValue(WEAK_REJECTING_SUSPENDED);

      // Iteratively look for states that belong to a weak accepting or rejecting SCC and replace
      // them by a constant, clean-up and repeat.
      while (true) {
        var firstWeakIndex = classification.entrySet().stream()
          .filter(entry -> entry.getValue() == WEAK_ACCEPTING_NOT_SUSPENDED
            || entry.getValue() == WEAK_REJECTING_NOT_SUSPENDED)
          .findFirst();

        if (firstWeakIndex.isEmpty()) {
          break;
        }

        int weakIndex = firstWeakIndex.get().getKey();
        var value =
          firstWeakIndex.get().getValue() == WEAK_ACCEPTING_NOT_SUSPENDED
            ? PropositionalFormula.<Integer>trueConstant()
            : PropositionalFormula.<Integer>falseConstant();

        alpha = alpha.substitute(i -> i == weakIndex
          ? value
          : PropositionalFormula.Variable.of(i));

        var remainingVariables = alpha.variables();
        classification.entrySet().forEach(entry -> {
          if (remainingVariables.contains(entry.getKey())) {
            return;
          }

          if (entry.getKey() == weakIndex) {
            entry.setValue(NOT_SUSPENDABLE);
            return;
          }

          assert Set.of(
            WEAK_ACCEPTING_NOT_SUSPENDED,
            WEAK_REJECTING_NOT_SUSPENDED,
            SUSPENDABLE,
            NOT_SUSPENDABLE).contains(entry.getValue());

          if (entry.getValue() != NOT_SUSPENDABLE) {
            entry.setValue(SUSPENDABLE);
            stateMap.computeIfPresent(entry.getKey(), (x, y) -> y.suspend());
          }
        });
      }

      assert Set.of(SUSPENDABLE, NOT_SUSPENDABLE).containsAll(classification.values());
      assert classification.keySet().containsAll(alpha.variables());

      // Apply round-robin suspension.
      var newRoundRobinCounters = new BitSet();
      var roundRobinChains = roundRobinSuspension(
        alpha, stateMap, acceptingEdges, oldRoundRobinCounters, newRoundRobinCounters);

      alpha = alpha.substitute(x -> {
        ImmutableBitSet roundRobinChain = Iterables.getOnlyElement(
          roundRobinChains.stream().filter(chain -> chain.contains(x)).collect(Collectors.toList()),
          null);

        return roundRobinChain == null
          ? PropositionalFormula.Variable.of(x)
          : PropositionalFormula.Variable.of(roundRobinChain.first().orElseThrow());
      });

      var state = State.of(newStateFormula, stateMap, newRoundRobinCounters);
      var oldAlpha = alphaCache.put(state, alpha);
      assert oldAlpha == null || alpha.equals(oldAlpha);
      return state;
    }

    private NavigableSet<Integer> roundRobinChain(
      PropositionalFormula.Conjunction<Integer> conjunction) {

      var roundRobinChain = new TreeSet<Integer>();

      for (PropositionalFormula<Integer> conjunct : conjunction.conjuncts) {
        if (conjunct instanceof PropositionalFormula.Variable) {
          int variable = ((PropositionalFormula.Variable<Integer>) conjunct).variable;

          if (roundRobinCandidates.contains(variable)) {
            roundRobinChain.add(variable);
          }
        }
      }

      return roundRobinChain;
    }

    private NavigableSet<Integer> roundRobinChain(
      PropositionalFormula.Disjunction<Integer> disjunction) {

      var roundRobinChain = new TreeSet<Integer>();

      for (PropositionalFormula<Integer> disjunct : disjunction.disjuncts) {
        if (disjunct instanceof PropositionalFormula.Negation) {
          var negation = (PropositionalFormula.Negation<Integer>) disjunct;

          if (negation.operand instanceof PropositionalFormula.Variable) {
            int variable = ((PropositionalFormula.Variable<Integer>) negation.operand).variable;

            if (roundRobinCandidates.contains(variable)) {
              roundRobinChain.add(variable);
            }
          }
        }
      }

      return roundRobinChain;
    }

    private NavigableSet<Integer> roundRobinChain(PropositionalFormula<Integer> formula) {
      if (formula instanceof PropositionalFormula.Variable
        || formula instanceof PropositionalFormula.Negation
        || formula instanceof PropositionalFormula.Biconditional) {
        return new TreeSet<>();
      }

      if (formula instanceof PropositionalFormula.Conjunction) {
        return roundRobinChain((PropositionalFormula.Conjunction<Integer>) formula);
      }

      return roundRobinChain((PropositionalFormula.Disjunction<Integer>) formula);
    }

    private List<ImmutableBitSet> roundRobinSuspension(
      PropositionalFormula<Integer> alpha,
      Map<Integer, BreakpointStateRejecting> stateMap,
      BitSet acceptingEdges,
      ImmutableBitSet oldRoundRobinCounters,
      BitSet newRoundRobinCounters) {

      var roundRobinChains = new ArrayList<ImmutableBitSet>();
      var roundRobinChain = roundRobinChain(alpha);

      // Only do round-robin operations if the chain is non-trivial.
      if (roundRobinChain.size() >= 2) {

        // Look for the current active index of the chain
        var intersection = oldRoundRobinCounters.intersection(roundRobinChain);
        assert intersection.size() < 2;
        int currentIndex = intersection.first().orElse(roundRobinChain.first());

        // Check if all elements are accepting.
        boolean fullCircle = true;

        // Skip around
        for (int nextIndex : Iterables.concat(
          roundRobinChain.tailSet(currentIndex, true),
          roundRobinChain.headSet(currentIndex, false))) {

          if (!acceptingEdges.get(nextIndex)) {
            currentIndex = nextIndex;
            fullCircle = false;
            break;
          }
        }

        // Reset to a default value.
        if (fullCircle) {
          currentIndex = roundRobinChain.first();
        }

        // Suspend other elements of the chain.
        for (Map.Entry<Integer, BreakpointStateRejecting> entry : stateMap.entrySet()) {
          if (roundRobinChain.contains(entry.getKey())
            && entry.getKey() != currentIndex) {

            entry.setValue(entry.getValue().suspend());
          }
        }

        newRoundRobinCounters.set(currentIndex);
        roundRobinChains.add(ImmutableBitSet.copyOf(roundRobinChain));
      }

      if (alpha instanceof PropositionalFormula.Biconditional) {
        var castedAlpha = (PropositionalFormula.Biconditional<Integer>) alpha;

        roundRobinChains.addAll(
          roundRobinSuspension(
            castedAlpha.leftOperand,
            stateMap,
            acceptingEdges,
            oldRoundRobinCounters,
            newRoundRobinCounters));

        roundRobinChains.addAll(
          roundRobinSuspension(
            castedAlpha.rightOperand,
            stateMap,
            acceptingEdges,
            oldRoundRobinCounters,
            newRoundRobinCounters));

      } else if (alpha instanceof PropositionalFormula.Conjunction) {
        for (var conjunct : ((PropositionalFormula.Conjunction<Integer>) alpha).conjuncts) {
          roundRobinChains.addAll(
            roundRobinSuspension(
              conjunct, stateMap, acceptingEdges, oldRoundRobinCounters, newRoundRobinCounters));
        }
      } else if (alpha instanceof PropositionalFormula.Disjunction) {
        for (var disjunct : ((PropositionalFormula.Disjunction<Integer>) alpha).disjuncts) {
          roundRobinChains.addAll(
            roundRobinSuspension(
              disjunct, stateMap, acceptingEdges, oldRoundRobinCounters, newRoundRobinCounters));
        }
      } else {
        assert alpha instanceof PropositionalFormula.Negation
          || alpha instanceof PropositionalFormula.Variable;
      }

      return roundRobinChains;
    }

    private PropositionalFormula<Integer> pruneRedundantConjunctsAndDisjuncts(
      PropositionalFormula<Integer> stateFormula, Map<Integer, BreakpointStateRejecting> stateMap) {

      if (stateFormula instanceof PropositionalFormula.Variable) {
        return stateFormula;
      }

      if (stateFormula instanceof PropositionalFormula.Negation) {
        return PropositionalFormula.Negation.of(pruneRedundantConjunctsAndDisjuncts(
          ((PropositionalFormula.Negation<Integer>) stateFormula).operand, stateMap));
      }

      if (stateFormula instanceof PropositionalFormula.Biconditional) {
        var castedStateFormula = (PropositionalFormula.Biconditional<Integer>) stateFormula;

        if (isVariableOrNegationOfVariable(castedStateFormula.leftOperand)
          && isVariableOrNegationOfVariable(castedStateFormula.rightOperand)) {

          var leftLanguage = language(castedStateFormula.leftOperand, stateMap);
          var rightLanguage = language(castedStateFormula.rightOperand, stateMap);

          if (leftLanguage.equals(rightLanguage)) {
            return PropositionalFormula.trueConstant();
          }

          if (leftLanguage.equals(rightLanguage.not())) {
            return PropositionalFormula.falseConstant();
          }
        }

        return PropositionalFormula.Biconditional.of(
          pruneRedundantConjunctsAndDisjuncts(castedStateFormula.leftOperand, stateMap),
          pruneRedundantConjunctsAndDisjuncts(castedStateFormula.rightOperand, stateMap));
      }

      if (stateFormula instanceof PropositionalFormula.Conjunction) {

        return PropositionalFormula.Conjunction.of(Collections3.maximalElements(
          ((PropositionalFormula.Conjunction<Integer>) stateFormula).conjuncts.stream()
            .map(x -> pruneRedundantConjunctsAndDisjuncts(x, stateMap))
            .collect(Collectors.toList()),
          (x, y) -> {
            if (!isVariableOrNegationOfVariable(x) || !isVariableOrNegationOfVariable(y)) {
              return false;
            }

            var xLanguage = language(x, stateMap);
            var yLanguage = language(y, stateMap);

            // If they don't share temporal operators, don't try to compute language implication.
            if (Collections.disjoint(
              xLanguage.temporalOperators(), yLanguage.temporalOperators())) {

              return false;
            }

            return yLanguage.implies(xLanguage);
          }));
      }

      assert stateFormula instanceof PropositionalFormula.Disjunction;

      return PropositionalFormula.Disjunction.of(Collections3.maximalElements(
        ((PropositionalFormula.Disjunction<Integer>) stateFormula).disjuncts.stream()
          .map(x -> pruneRedundantConjunctsAndDisjuncts(x, stateMap))
          .collect(Collectors.toList()),
        (x, y) -> {
          if (!isVariableOrNegationOfVariable(x) || !isVariableOrNegationOfVariable(y)) {
            return false;
          }

          var xLanguage = language(x, stateMap);
          var yLanguage = language(y, stateMap);

          // If they don't share temporal operators, don't try to compute language implication.
          if (Collections.disjoint(
            xLanguage.temporalOperators(), yLanguage.temporalOperators())) {

            return false;
          }

          return xLanguage.implies(yLanguage);
        }));
    }

    private static boolean isVariableOrNegationOfVariable(PropositionalFormula<?> formula) {
      return formula instanceof PropositionalFormula.Variable
        || (formula instanceof PropositionalFormula.Negation
        && ((PropositionalFormula.Negation<?>) formula).operand
        instanceof PropositionalFormula.Variable);
    }

    private static EquivalenceClass language(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, ? extends BreakpointStateRejecting> stateMap) {

      assert isVariableOrNegationOfVariable(stateFormula);

      if (stateFormula instanceof PropositionalFormula.Variable) {
        int index = ((PropositionalFormula.Variable<Integer>) stateFormula).variable;
        return stateMap.get(index).all();
      }

      assert stateFormula instanceof PropositionalFormula.Negation;
      var operand = ((PropositionalFormula.Negation<Integer>) stateFormula).operand;

      assert operand instanceof PropositionalFormula.Variable;
      int index = ((PropositionalFormula.Variable<Integer>) operand).variable;

      return stateMap.get(index).all().not();
    }

    public PropositionalFormula<Integer> alpha(State state) {
      return alphaCache.get(state);
    }

    public PropositionalFormula<Integer> beta(State state) {
      return betaCache.computeIfAbsent(state, this::computeBeta);
    }

    // TODO: build implication graph and propagate information for state formula?
    private PropositionalFormula<Integer> computeBeta(State state) {
      var variables = alpha(state).variables();

      List<PropositionalFormula<Integer>> facts = new ArrayList<>();

      for (int var1 : variables) {
        for (int var2 : variables) {
          if (var1 == var2) {
            continue;
          }

          var state1 = state.stateMap().get(var1);
          var state2 = state.stateMap().get(var2);

          var clazz1 = state1.all();
          var clazz2 = state2.all();

          if (Collections.disjoint(clazz1.temporalOperators(), clazz2.temporalOperators())) {
            continue;
          }

          if (clazz1.implies(clazz2)) {
            var neg1 = PropositionalFormula.Negation.of(PropositionalFormula.Variable.of(var1));
            var pos2 = PropositionalFormula.Variable.of(var2);

            facts.add(PropositionalFormula.Disjunction.of(neg1, pos2));
          }

          //          var dbw1 = Views.replaceInitialStates(dbw, Set.of(state1));
          //          var dbw2 = Views.replaceInitialStates(dbw, Set.of(state2));
          //
          //          if (LanguageContainment.contains(dbw1, dbw2)) {
          //            if (clazz1.implies(clazz2) || clazz1.not().or(clazz2).isTrue()) {
          //              System.err.println("found (impl): " + clazz1 + " " + clazz2);
          //            }
          //
          //            var neg1 = PropositionalFormula.Negation.of(PropositionalFormula
          //            .Variable.of(var1));
          //            var pos2 = PropositionalFormula.Variable.of(var2);
          //
          //            facts.add(PropositionalFormula.Disjunction.of(neg1, pos2));
        }
      }

      return PropositionalFormula.Conjunction.of(facts);
    }
  }

  private static final class CountingVisitor extends PropositionalVisitor<Void> {

    private final Map<Formula.TemporalOperator, Integer> referenceCounter = new HashMap<>();

    @Override
    protected Void visit(Formula.TemporalOperator formula) {
      if (referenceCounter.containsKey(formula)) {
        referenceCounter.put(formula, 2);
        return null;
      }

      var formulaNot = (Formula.TemporalOperator) formula.not();

      if (referenceCounter.containsKey(formulaNot)) {
        referenceCounter.put(formulaNot, 2);
        return null;
      }

      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        referenceCounter.put(formula, 1);
        return null;
      }

      if (SyntacticFragments.isSafetyCoSafety(formulaNot)) {
        referenceCounter.put(formulaNot, 1);
        return null;
      }

      throw new AssertionError("should not be reachable.");
    }

    @Override
    public Void visit(Literal literal) {
      return null;
    }

    @Override
    public Void visit(Biconditional biconditional) {
      biconditional.operands.forEach(x -> x.accept(this));
      return null;
    }

    @Override
    public Void visit(BooleanConstant booleanConstant) {
      return null;
    }

    @Override
    public Void visit(Conjunction conjunction) {
      conjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @Override
    public Void visit(Disjunction disjunction) {
      disjunction.operands.forEach(x -> x.accept(this));
      return null;
    }

    @Override
    public Void visit(Negation negation) {
      return negation.operand().accept(this);
    }
  }

  private static final class NormalFormConverter
    extends PropositionalVisitor<Formula> {

    @Override
    protected Formula visit(Formula.TemporalOperator formula) {
      // Keep this in sync with initial state constructor.
      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        return formula;
      }

      var formulaNot = (Formula.TemporalOperator) formula.not();

      if (SyntacticFragments.isSafetyCoSafety(formulaNot)) {
        return formula;
      }

      var normalForm1 =
        PushNextThroughPropositionalVisitor.apply(
          SimplifierRepository.SYNTACTIC_FIXPOINT.apply(
            NORMALISATION.apply(formula)));

      var normalForm2 =
        PushNextThroughPropositionalVisitor.apply(
          SimplifierRepository.SYNTACTIC_FIXPOINT.apply(
            DUAL_NORMALISATION.apply(formula)));

      if (normalForm1 instanceof Disjunction) {
        if (normalForm2 instanceof Disjunction
          && normalForm1.operands.size() < normalForm2.operands.size()) {
          return normalForm1.accept(this);
        }

        return normalForm2.accept(this);
      }

      if (normalForm1 instanceof Conjunction) {
        if (normalForm2 instanceof Disjunction
          || (normalForm2 instanceof Conjunction
          && normalForm1.operands.size() < normalForm2.operands.size())) {
          return normalForm1.accept(this);
        }

        return normalForm2.accept(this);
      }

      if (normalForm1.operands.size() < normalForm2.operands.size()) {
        return normalForm1.accept(this);
      }

      return normalForm2.accept(this);
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(Biconditional biconditional) {
      return Biconditional.of(
        biconditional.leftOperand().accept(this),
        biconditional.rightOperand().accept(this));
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(operand -> operand.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(operand -> operand.accept(this)));
    }
  }

  private static final class InitialStateConstructor
    extends PropositionalVisitor<PropositionalFormula<Integer>> {

    private final SafetyCoSafety dbw;
    private final List<BreakpointStateRejecting> initialStates = new ArrayList<>();
    private final BiMap<Formula, Integer> mapping = HashBiMap.create();
    private final Set<Formula.TemporalOperator> nonUnique;
    private final BitSet roundRobinCandidate = new BitSet();

    public InitialStateConstructor(
      SafetyCoSafety dbw,
      Set<Formula.TemporalOperator> nonUnique) {

      this.dbw = dbw;
      this.nonUnique = nonUnique;
    }

    private boolean uniqueReference(Formula.TemporalOperator formula) {
      assert SyntacticFragments.isSafetyCoSafety(formula)
        || SyntacticFragments.isCoSafetySafety(formula);

      return !nonUnique.contains(formula)
        && !nonUnique.contains((Formula.TemporalOperator) formula.not());
    }

    private PropositionalFormula<Integer> add(Formula formula) {
      if (mapping.containsKey(formula)) {
        int id = mapping.get(formula);
        return PropositionalFormula.Variable.of(id);
      }

      Formula formulaNot = formula.not();

      if (mapping.containsKey(formulaNot)) {
        int id = mapping.get(formulaNot);
        return PropositionalFormula.Negation.of(PropositionalFormula.Variable.of(id));
      }

      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        int id = mapping.size();
        var initialState = dbw.initialState(formula);
        mapping.put(formula, id);
        initialStates.add(initialState);
        return PropositionalFormula.Variable.of(id);
      }

      if (SyntacticFragments.isSafetyCoSafety(formulaNot)) {
        int id = mapping.size();
        var initialState = dbw.initialState(formulaNot);
        mapping.put(formulaNot, id);
        initialStates.add(initialState);
        return PropositionalFormula.Negation.of(PropositionalFormula.Variable.of(id));
      }

      throw new AssertionError("should not reach");
    }

    @Override
    protected PropositionalFormula<Integer> visit(Formula.TemporalOperator temporalOperator) {

      return add(temporalOperator);
    }

    @Override
    public PropositionalFormula<Integer> visit(Literal literal) {

      return add(literal);
    }

    @Override
    public PropositionalFormula<Integer> visit(BooleanConstant booleanConstant) {

      return booleanConstant.value
        ? PropositionalFormula.trueConstant()
        : PropositionalFormula.falseConstant();
    }

    @Override
    public PropositionalFormula<Integer> visit(Biconditional biconditional) {

      return PropositionalFormula.Biconditional.of(
        biconditional.leftOperand().accept(this),
        biconditional.rightOperand().accept(this));
    }

    @Override
    public PropositionalFormula<Integer> visit(Conjunction conjunction) {

      List<PropositionalFormula<Integer>> operands = new ArrayList<>(conjunction.operands.size());
      List<Formula> weakOrCoSafetySafety = new ArrayList<>();

      for (Formula operand : conjunction.operands) {
        boolean isWeak = SyntacticFragments.DELTA_1.contains(operand);
        boolean isCoSafetySafetyAndUnique = operand instanceof Formula.TemporalOperator
          && SyntacticFragments.isCoSafetySafety(operand)
          && uniqueReference((Formula.TemporalOperator) operand);
        boolean isChainable = SyntacticFragments.isGfCoSafety(operand)
          && uniqueReference((Formula.TemporalOperator) operand);

        if (isWeak || isCoSafetySafetyAndUnique) {
          weakOrCoSafetySafety.add(operand);
        } else if (isChainable) {
          assert operand instanceof GOperator;
          operands.add(add(operand));
          roundRobinCandidate.set(mapping.get(operand));
        } else {
          operands.add(operand.accept(this));
        }
      }

      if (!weakOrCoSafetySafety.isEmpty()) {
        operands.add(add(Conjunction.of(weakOrCoSafetySafety)));
      }

      return PropositionalFormula.Conjunction.of(operands);
    }

    @Override
    public PropositionalFormula<Integer> visit(Disjunction disjunction) {

      List<PropositionalFormula<Integer>> operands = new ArrayList<>(disjunction.operands.size());
      List<Formula> weakOrSafetyCoSafety = new ArrayList<>();

      for (Formula operand : disjunction.operands) {
        boolean isWeak = SyntacticFragments.DELTA_1.contains(operand);
        boolean isSafetyCoSafetyAndUnique = operand instanceof Formula.TemporalOperator
          && SyntacticFragments.isSafetyCoSafety(operand)
          && uniqueReference((Formula.TemporalOperator) operand);
        boolean isChainable = SyntacticFragments.isFgSafety(operand)
          && uniqueReference((Formula.TemporalOperator) operand);

        if (isWeak || isSafetyCoSafetyAndUnique) {
          weakOrSafetyCoSafety.add(operand);
        } else if (isChainable) {
          assert operand instanceof FOperator;
          operands.add(add(operand));
          // Internally the automaton for the negation is constructed, thus we flip.
          roundRobinCandidate.set(mapping.get(operand.not()));
        } else {
          operands.add(operand.accept(this));
        }
      }

      if (!weakOrSafetyCoSafety.isEmpty()) {
        operands.add(add(Disjunction.of(weakOrSafetyCoSafety)));
      }

      return PropositionalFormula.Disjunction.of(operands);
    }
  }

  @AutoValue
  public abstract static class State {

    public abstract PropositionalFormula<Integer> stateFormula();

    public abstract Map<Integer, BreakpointStateRejecting> stateMap();

    public abstract ImmutableBitSet roundRobinCounters();

    public static State of(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, BreakpointStateRejecting> stateMap,
      BitSet counters) {

      return of(stateFormula, stateMap, ImmutableBitSet.copyOf(counters));
    }

    public static State of(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, BreakpointStateRejecting> stateMap,
      Set<Integer> counters) {

      assert stateFormula.variables().equals(stateMap.keySet());
      assert stateMap.keySet().containsAll(counters);

      return new AutoValue_NormalformDELAConstruction_State(
        stateFormula, Map.copyOf(stateMap), ImmutableBitSet.copyOf(counters));
    }

    public boolean inDifferentSccs(State otherState) {
      if (!stateFormula().equals(otherState.stateFormula())) {
        return true;
      }

      if (stateMap().size() != otherState.stateMap().size()) {
        return true;
      }

      for (var entry : stateMap().entrySet()) {
        var value2 = otherState.stateMap().get(entry.getKey());

        if (value2 == null) {
          return true;
        }

        var all1 = entry.getValue().all();
        var all2 = value2.all();

        if (BlockingElements.surelyContainedInDifferentSccs(all1, all2)) {
          return true;
        }
      }

      return false;
    }
  }

  static class BreakpointStateRejectingClassifier {

    private final SafetyCoSafety dbw;
    private final OptionalInt lookahead;
    private final Map<BreakpointStateRejecting, Classification> memoizedResults = new HashMap<>();

    BreakpointStateRejectingClassifier(SafetyCoSafety dbw, OptionalInt lookahead) {
      this.dbw = dbw;
      this.lookahead = lookahead;
    }

    // This should only be called on unsuspended states (or by SafetyCoSafety suspended states).
    Classification classify(BreakpointStateRejecting state) {
      Classification classification = memoizedResults.get(state);

      if (classification != null) {
        return classification;
      }

      classification = classifySyntactically(state);

      if (classification != null) {
        return classification;
      }

      var restrictedDbw = Views.filtered(dbw, Views.Filter.<BreakpointStateRejecting>builder()
        .initialStates(Set.of(state))
        .edgeFilter((dbwState, edge) ->
          !BlockingElements.surelyContainedInDifferentSccs(dbwState.all(), edge.successor().all()))
        .build());

      // The restricted automaton is too large, thus we skip the semantic analysis.
      if (lookahead.isPresent()
        && !AutomatonUtil.isLessOrEqual(restrictedDbw, lookahead.getAsInt())) {

        return memoize(state, NOT_SUSPENDABLE);
      }

      var sccDecomposition = SccDecomposition.of(restrictedDbw);

      for (Set<BreakpointStateRejecting> scc : sccDecomposition.sccs()) {
        for (BreakpointStateRejecting sccState : scc) {
          // We already computed a value for the scc state.
          if (memoizedResults.containsKey(sccState)) {
            continue;
          }

          // If the Scc does not contain the state we started with, then a syntactic classification
          // might suffice.
          if (scc.contains(state) || classifySyntactically(sccState) == null) {
            classifySemantically(sccState, sccDecomposition);
          }
        }
      }

      classification = memoizedResults.get(state);
      assert classification != null;
      return classification;
    }

    // This should only be called on unsuspended states (or by SafetyCoSafety suspended states).
    NavigableMap<Integer, Classification> classify(
      Map<Integer, ? extends BreakpointStateRejecting> stateMap) {

      var map = new TreeMap<Integer, Classification>();

      stateMap.forEach((key, state) -> {
        map.put(key, classify(state));
      });

      return map;
    }

    @Nullable
    private Classification classifySyntactically(
      BreakpointStateRejecting state) {

      assert !memoizedResults.containsKey(state);

      if (state.all().isTrue()) {
        assert state.isSuspended();
        return memoize(state, TERMINAL_ACCEPTING);
      }

      if (state.all().isFalse()) {
        assert state.isSuspended();
        return memoize(state, TERMINAL_REJECTING);
      }

      // Replace these by other checks...
      if (dbw.suspensionCheck.isBlockedByTransient(state.all())) {
        assert state.isSuspended();
        return memoize(state, TRANSIENT_SUSPENDED);
      }

      if (dbw.suspensionCheck.isBlockedBySafety(state.all())) {
        assert state.isSuspended();
        return memoize(state, WEAK_ACCEPTING_SUSPENDED);
      }

      if (dbw.suspensionCheck.isBlockedByCoSafety(state.all())) {
        assert state.isSuspended();
        return memoize(state, WEAK_REJECTING_SUSPENDED);
      }

      return null;
    }

    private Classification classifySemantically(
      BreakpointStateRejecting state, SccDecomposition<BreakpointStateRejecting> sccDecomposition) {

      assert !memoizedResults.containsKey(state);
      assert !state.isSuspended();

      int index = sccDecomposition.index(state);

      // Check for transient SCCs.
      if (sccDecomposition.transientSccs().contains(index)) {
        return memoize(state, TRANSIENT_NOT_SUSPENDED);
      }

      if (sccDecomposition.acceptingSccs().contains(index)) {
        return memoize(sccDecomposition.sccs().get(index), WEAK_ACCEPTING_NOT_SUSPENDED);
      }

      if (sccDecomposition.rejectingSccs().contains(index)) {
        return memoize(sccDecomposition.sccs().get(index), WEAK_REJECTING_NOT_SUSPENDED);
      }

      var scc = sccDecomposition.sccs().get(index);

      if (scc.size() == Collections3.transformSet(scc, BreakpointStateRejecting::all).size()) {
        return memoize(scc, NOT_SUSPENDABLE);
      }

      return memoize(scc, SUSPENDABLE);
    }

    private Classification memoize(
      BreakpointStateRejecting state, Classification classification) {

      var oldStatus = memoizedResults.put(state, classification);
      assert oldStatus == null || oldStatus == classification;
      return classification;
    }

    private Classification memoize(
      Set<? extends BreakpointStateRejecting> scc, Classification classification) {

      scc.forEach(x -> memoize(x, classification));
      return classification;
    }
  }

  enum Classification {
    TERMINAL_ACCEPTING, // The state is terminal accepting, e.g., an accepting sink.
    TERMINAL_REJECTING, // The state is terminal rejecting, e.g., a rejecting sink.

    TRANSIENT_SUSPENDED, // The state is in a transient SCC and suspended.
    TRANSIENT_NOT_SUSPENDED, // The state is in a transient SCC and not suspended.

    WEAK_ACCEPTING_SUSPENDED,
    WEAK_ACCEPTING_NOT_SUSPENDED,

    WEAK_REJECTING_SUSPENDED,
    WEAK_REJECTING_NOT_SUSPENDED,

    SUSPENDABLE,
    NOT_SUSPENDABLE
  }
}
