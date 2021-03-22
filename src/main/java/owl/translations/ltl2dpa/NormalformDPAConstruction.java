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

package owl.translations.ltl2dpa;

import static owl.translations.canonical.DeterministicConstructions.BreakpointStateRejecting;
import static owl.translations.canonical.DeterministicConstructions.SafetyCoSafety;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.NOT_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.TERMINAL_ACCEPTING;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.TERMINAL_REJECTING;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.TRANSIENT_NOT_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.TRANSIENT_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.WEAK_ACCEPTING_NOT_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.WEAK_ACCEPTING_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.WEAK_REJECTING_NOT_SUSPENDABLE;
import static owl.translations.ltl2dpa.NormalformDPAConstruction.Status.WEAK_REJECTING_SUSPENDABLE;

import com.google.auto.value.AutoValue;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Table;
import com.google.common.graph.Graphs;
import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.cli.Options;
import owl.automaton.AbstractMemoizingAutomaton;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.ToParityTransformer;
import owl.automaton.algorithm.SccDecomposition;
import owl.automaton.edge.Colours;
import owl.automaton.edge.Edge;
import owl.bdd.FactorySupplier;
import owl.bdd.MtBdd;
import owl.bdd.MtBddOperations;
import owl.collections.Collections3;
import owl.logic.propositional.PropositionalFormula;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.PushNextThroughPropositionalVisitor;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierTransformer;
import owl.ltl.visitors.PropositionalVisitor;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModule;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;
import owl.translations.mastertheorem.Normalisation;

public final class NormalformDPAConstruction implements
  Function<LabelledFormula, Automaton<NormalformDPAConstruction.State, ParityAcceptance>> {

  private static final Normalisation NORMALISATION
    = Normalisation.of(false, false, false);

  private static final Normalisation DUAL_NORMALISATION
    = Normalisation.of(true, false, false);

  public static final OwlModule<OwlModule.Transformer> MODULE = OwlModule.of(
    "ltl2dpaZielonka",
    "Translate LTL to DPAs using normalforms and zielonka split trees.",
    new Options().addOption(null, "simple", false,
      "Disable optimisations and only use the simple base construction."),
    (commandLine, environment) ->
      OwlModule.LabelledFormulaTransformer.of(
        x -> of(commandLine.hasOption("simple")).apply(x))
  );

  private final boolean simpleConstruction;

  public NormalformDPAConstruction(boolean simpleConstruction) {
    this.simpleConstruction = simpleConstruction;
  }

  public static void main(String... args) throws IOException {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.of(
      InputReaders.LTL_INPUT_MODULE,
      List.of(SimplifierTransformer.MODULE),
      MODULE,
      List.of(AcceptanceOptimizations.MODULE),
      OutputWriters.HOA_OUTPUT_MODULE));
  }

  public static NormalformDPAConstruction of(boolean simpleConstruction) {
    return new NormalformDPAConstruction(simpleConstruction);
  }

  @Override
  public Automaton<State, ParityAcceptance> apply(LabelledFormula formula) {
    // Ensure that the input formula is in negation normal form and that
    // X-operators occur only in-front of temporal operators.
    var normalisedFormula = PushNextThroughPropositionalVisitor.apply(formula.nnf());
    return new Construction(normalisedFormula, simpleConstruction).automaton();
  }

  private static class Construction {

    // Underlying DBW.
    private final SafetyCoSafety dbw;

    private final Map<BreakpointStateRejecting, Status> statusCache = new HashMap<>();
    private final Table<PropositionalFormula<Integer>, PropositionalFormula<Integer>,
      ToParityTransformer.ZielonkaTreeRoot> zielonkaTreeCache = HashBasedTable.create();
    private final LabelledFormula formula;

    private final boolean simpleConstruction;

    private Construction(LabelledFormula formula, boolean simpleConstruction) {

      var factories
        = FactorySupplier.defaultSupplier().getFactories(formula.atomicPropositions());

      // Underlying automaton.
      this.dbw = SafetyCoSafety.of(factories, BooleanConstant.TRUE, true, true);
      this.formula = formula;
      this.simpleConstruction = simpleConstruction;
    }

    private Automaton<State, ParityAcceptance> automaton() {
      var initialState = initialState();
      var acceptance = new ParityAcceptance(
        initialState.stateMap().size() + 2, ParityAcceptance.Parity.MIN_EVEN);

      return new AbstractMemoizingAutomaton.EdgeTreeImplementation<>(
        dbw.atomicPropositions(), dbw.factory(), Set.of(initialState), acceptance) {

        @Override
        protected MtBdd<Edge<State>> edgeTreeImpl(State state) {
          var edgeTreeMap = new HashMap<>(Maps.transformValues(state.stateMap(), dbw::edgeTree));

          // Remember, dbw needs to be complete!
          return MtBddOperations.uncachedNaryCartesianProduct(edgeTreeMap, y -> {
            var edge = combineEdge(state, y);

            if (edge.successor().stateFormula().isFalse()) {
              return null;
            }

            return edge;
          });
        }
      };
    }

    private Edge<State> combineEdge(State state,
      Collection<Map.Entry<Integer, Edge<BreakpointStateRejecting>>> edges) {

      BitSet acceptanceSet = new BitSet();

      Map<Integer, BreakpointStateRejecting> stateMap = new HashMap<>(edges.size());
      edges.forEach((entry) -> {
        var oldValue
          = stateMap.put(entry.getKey(), entry.getValue().successor());
        assert oldValue == null;

        if (entry.getValue().colours().contains(0)) {
          acceptanceSet.set(entry.getKey());
        }
      });

      NavigableMap<Integer, Status> statusMap = computeStatus(stateMap);

      // Construct new PropositionalFormula + Integer.
      PropositionalFormula<Integer> successorFormula
        = updateStateFormula(state.stateFormula(), stateMap, statusMap).normalise();

      // Compute \alpha.
      PropositionalFormula<Integer> alpha = alpha(successorFormula, statusMap);
      // Compute \beta.
      PropositionalFormula<Integer> beta = beta(successorFormula.variables(), stateMap);

      ToParityTransformer.ZielonkaTreeRoot zielonkaTreeRoot
        = zielonkaTreeCache.get(alpha, beta);

      // Not yet, computed. Computing now.
      if (zielonkaTreeRoot == null) {
        zielonkaTreeRoot
          = ToParityTransformer.ZielonkaTreeRoot.of(alpha, beta, true);
        zielonkaTreeCache.put(alpha, beta, zielonkaTreeRoot);
      }

      State successor;
      int colour = -1;

      // Is it the same?
      if (state.zielonkaTreeRoot().equals(zielonkaTreeRoot)) {
        // Yes, Update path and generate colours
        var zielonkaEdge = zielonkaTreeRoot.transformColours(
          Colours.copyOf(acceptanceSet), state.zielonkaTreePath());
        successor = State.of(successorFormula,
          stateMap, zielonkaTreeRoot, zielonkaEdge.successor(), statusMap);
        colour = zielonkaEdge.smallestAcceptanceSet();
      } else {
        // No, return updated state, but no colour.
        successor = State.of(successorFormula, stateMap,
          zielonkaTreeRoot, zielonkaTreeRoot.initialExtension(), statusMap);
      }

      return colour == -1 ? Edge.of(successor) : Edge.of(successor, colour);
    }

    private State initialState() {
      var normalFormConstructor = new NormalFormConverter(simpleConstruction);
      var normalForm = formula.formula().accept(normalFormConstructor);
      normalFormConstructor.referenceCounter.entrySet().removeIf(e -> e.getValue().equals(1));
      var initialStateFormulaConstructor = new InitialStateConstructor(
        dbw, simpleConstruction, normalFormConstructor.referenceCounter.keySet());
      var initialStateFormula = initialStateFormulaConstructor.apply(normalForm);
      var initialStates = initialStateFormulaConstructor.initialStates;

      Map<Integer, BreakpointStateRejecting> stateMap = new HashMap<>(initialStates.size());

      for (int i = 0, s = initialStates.size(); i < s; i++) {
        stateMap.put(i, initialStates.get(i));
      }

      NavigableMap<Integer, Status> statusMap = computeStatus(stateMap);

      initialStateFormula = updateStateFormula(initialStateFormula, stateMap, statusMap);
      PropositionalFormula<Integer> alpha = alpha(initialStateFormula, statusMap);
      PropositionalFormula<Integer> beta = beta(initialStateFormula.variables(), stateMap);

      var zielonkaTreeTransformer
        = ToParityTransformer.ZielonkaTreeRoot.of(alpha, beta, true);

      return State.of(
        initialStateFormula,
        stateMap,
        zielonkaTreeTransformer,
        zielonkaTreeTransformer.initialExtension(),
        statusMap);
    }

    private Status memoize(Set<? extends BreakpointStateRejecting> scc, Status status) {
      scc.forEach(state -> {
        var oldStatus = statusCache.put(state, status);
        assert oldStatus == null || oldStatus == status;
      });

      return status;
    }

    private Status computeStatus(BreakpointStateRejecting state) {
      Status status = statusCache.get(state);

      if (status != null) {
        return status;
      }

      var automaton = Views.filtered(dbw, Views.Filter.of(Set.of(state)));
      var sccDecomposition = SccDecomposition.of(automaton);

      for (Set<BreakpointStateRejecting> scc : sccDecomposition.sccs()) {
        for (BreakpointStateRejecting sccState : scc) {
          computeStatus(sccState, sccDecomposition);
        }
      }

      return computeStatus(state, sccDecomposition);
    }

    // This is only called on unsuspended states (or by SafetyCoSafety suspended states).
    private Status computeStatus(BreakpointStateRejecting state,
      SccDecomposition<BreakpointStateRejecting> sccDecomposition) {

      Status status = statusCache.get(state);

      if (status != null) {
        return status;
      }

      // Check for accepting and rejecting sinks.
      if (state.all().isTrue()) {
        assert state.isSuspended();
        return memoize(Set.of(state), TERMINAL_ACCEPTING);
      }

      if (state.all().isFalse()) {
        assert state.isSuspended();
        return memoize(Set.of(state), TERMINAL_REJECTING);
      }

      var condensationGraph = sccDecomposition.condensation();
      int index = sccDecomposition.index(state);

      if (sccDecomposition.rejectingSccs().equals(
        Graphs.reachableNodes(condensationGraph, index))) {

        return memoize(Set.of(state), TERMINAL_REJECTING);
      }

      // Check for transient SCCs.
      if (dbw.suspensionCheck.isBlockedByTransient(state.all())) {
        assert state.isSuspended();
        return memoize(Set.of(state), TRANSIENT_SUSPENDABLE);
      }

      if (sccDecomposition.transientSccs().contains(index)) {
        return memoize(Set.of(state), state.isSuspended()
          ? TRANSIENT_SUSPENDABLE
          : TRANSIENT_NOT_SUSPENDABLE);
      }

      if (state.isSuspended() && dbw.suspensionCheck.isBlockedBySafety(state.all())) {
        return memoize(Set.of(state), WEAK_ACCEPTING_SUSPENDABLE);
      }

      if (state.isSuspended() && dbw.suspensionCheck.isBlockedByCoSafety(state.all())) {
        return memoize(Set.of(state), WEAK_REJECTING_SUSPENDABLE);
      }

      assert !state.isSuspended();

      if (sccDecomposition.acceptingSccs().contains(index)) {
        return memoize(sccDecomposition.sccs().get(index), WEAK_ACCEPTING_NOT_SUSPENDABLE);
      }

      if (sccDecomposition.rejectingSccs().contains(index)) {
        return memoize(sccDecomposition.sccs().get(index), WEAK_REJECTING_NOT_SUSPENDABLE);
      }

      var scc = sccDecomposition.sccs().get(index);

      if (scc.size() == Collections3.transformSet(scc, BreakpointStateRejecting::all).size()) {
        return memoize(scc, NOT_SUSPENDABLE);
      }

      assert !state.isSuspended();
      return memoize(scc, SUSPENDABLE);
    }

    NavigableMap<Integer, Status> computeStatus(
      Map<Integer, ? extends BreakpointStateRejecting> stateMap) {

      var map = new TreeMap<Integer, Status>();

      stateMap.forEach((key, state) -> {
        map.put(key, computeStatus(state));
      });

      return map;
    }

    private PropositionalFormula<Integer> updateStateFormula(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, ? extends BreakpointStateRejecting> stateMap,
      Map<Integer, Status> statusMap) {

      PropositionalFormula<Integer> newStateFormula
        = stateFormula.substitute(index -> {
          switch (statusMap.get(index)) {
            case TERMINAL_ACCEPTING:
              return Optional.of(PropositionalFormula.trueConstant());

            case TERMINAL_REJECTING:
              return Optional.of(PropositionalFormula.falseConstant());

            default:
              return Optional.empty();
          }
        }).normalise();

      newStateFormula = simpleConstruction
        ? newStateFormula
        : simplifyStateFormula(newStateFormula, stateMap, new HashMap<>()).normalise();

      var remainingVariables = newStateFormula.variables();
      statusMap.keySet().retainAll(remainingVariables);
      stateMap.keySet().retainAll(remainingVariables);

      return newStateFormula;
    }

    private static PropositionalFormula<Integer> simplifyStateFormula(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, ? extends BreakpointStateRejecting> stateMap,
      Map<PropositionalFormula<Integer>, EquivalenceClass> languageCache) {

      if (stateFormula.isTrue() || stateFormula.isFalse()) {
        return stateFormula;
      }

      // Nothing to do.
      if (stateFormula instanceof PropositionalFormula.Variable
        || stateFormula instanceof PropositionalFormula.Negation) {
        return stateFormula;
      }

      if (stateFormula instanceof PropositionalFormula.Conjunction) {
        var conjuncts = PropositionalFormula.conjuncts(stateFormula).stream()
          .map(x -> simplifyStateFormula(x, stateMap, languageCache))
          .collect(Collectors.toList());

        assert !conjuncts.isEmpty();

        var normalisedFormula
          = PropositionalFormula.Conjunction.of(conjuncts).normalise();

        if (normalisedFormula.isTrue() || normalisedFormula.isFalse()) {
          return normalisedFormula;
        }

        var language = language(normalisedFormula, stateMap, languageCache);

        if (language.isTrue()) {
          return PropositionalFormula.trueConstant();
        }

        if (language.isFalse()) {
          return PropositionalFormula.falseConstant();
        }

        conjuncts = Collections3.maximalElements(
          PropositionalFormula.conjuncts(normalisedFormula),
          (x, y) -> {
            return language(y, stateMap, languageCache).implies(
              language(x, stateMap, languageCache));
          });

        return PropositionalFormula.Conjunction.of(conjuncts).normalise();
      }

      assert stateFormula instanceof PropositionalFormula.Disjunction;
      var disjuncts = PropositionalFormula.disjuncts(stateFormula).stream()
        .map(x -> simplifyStateFormula(x, stateMap, languageCache))
        .collect(Collectors.toList());

      assert !disjuncts.isEmpty();

      var normalisedFormula
        = PropositionalFormula.Disjunction.of(disjuncts).normalise();

      if (normalisedFormula.isTrue() || normalisedFormula.isFalse()) {
        return normalisedFormula;
      }

      var language = language(normalisedFormula, stateMap, languageCache);

      if (language.isTrue()) {
        return PropositionalFormula.trueConstant();
      }

      if (language.isFalse()) {
        return PropositionalFormula.falseConstant();
      }

      disjuncts = Collections3.maximalElements(
        PropositionalFormula.disjuncts(normalisedFormula),
        (x, y) -> {
          return language(x, stateMap, languageCache).implies(
            language(y, stateMap, languageCache));
        });

      return PropositionalFormula.Disjunction.of(disjuncts).normalise();
    }

    private static EquivalenceClass language(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, ? extends BreakpointStateRejecting> stateMap,
      Map<PropositionalFormula<Integer>, EquivalenceClass> languageCache) {

      EquivalenceClass language = languageCache.get(stateFormula);

      if (language != null) {
        return language;
      }

      if (stateFormula instanceof PropositionalFormula.Variable) {
        language
          = stateMap.get(((PropositionalFormula.Variable<Integer>) stateFormula).variable).all();
      } else if (stateFormula instanceof PropositionalFormula.Negation) {
        var castedStateFormula
          = ((PropositionalFormula.Negation<Integer>) stateFormula);
        var complementLanguage
          = language(castedStateFormula.operand, stateMap, languageCache);
        var languageFormula = complementLanguage.canonicalRepresentativeDnf().not();
        language = complementLanguage.factory().of(languageFormula);
      } else if (stateFormula instanceof PropositionalFormula.Conjunction) {
        var castedStateFormula = ((PropositionalFormula.Conjunction<Integer>) stateFormula);
        language = castedStateFormula.conjuncts.stream()
          .map(conjunct -> language(conjunct, stateMap, languageCache))
          .reduce(EquivalenceClass::and)
          .orElseThrow();
      } else {
        var castedStateFormula = ((PropositionalFormula.Disjunction<Integer>) stateFormula);
        language = castedStateFormula.disjuncts.stream()
          .map(disjunct -> language(disjunct, stateMap, languageCache))
          .reduce(EquivalenceClass::or)
          .orElseThrow();
      }

      languageCache.put(stateFormula, language);
      return language;
    }

    private PropositionalFormula<Integer> alpha(
      PropositionalFormula<Integer> stateFormula, NavigableMap<Integer, Status> statusMap) {

      assert !statusMap.containsValue(TERMINAL_ACCEPTING);
      assert !statusMap.containsValue(TERMINAL_REJECTING);

      if (simpleConstruction) {
        statusMap.entrySet().forEach(entry -> entry.setValue(SUSPENDABLE));
        return stateFormula;
      }

      // If there is a state that is transient and can be suspended then suspend everything.
      if (statusMap.containsValue(TRANSIENT_SUSPENDABLE)) {
        statusMap.entrySet().forEach(entry -> entry.setValue(SUSPENDABLE));
        return PropositionalFormula.trueConstant();
      }

      assert !statusMap.containsValue(TRANSIENT_SUSPENDABLE);

      // If there is a state that is transient and but cannot be safely suspended, then suspend
      // everything except that state.
      Optional<Integer> firstTransientIndex = statusMap.entrySet().stream()
        .filter(entry -> entry.getValue() == TRANSIENT_NOT_SUSPENDABLE)
        .map(Map.Entry::getKey)
        .findFirst();

      if (firstTransientIndex.isPresent()) {
        Integer smallestTransientIndex = firstTransientIndex.get();

        statusMap.entrySet().forEach(entry -> {
          if (entry.getKey().equals(smallestTransientIndex)) {
            entry.setValue(NOT_SUSPENDABLE);
          } else {
            entry.setValue(SUSPENDABLE);
          }
        });

        return PropositionalFormula.trueConstant();
      }

      assert !statusMap.containsValue(TRANSIENT_NOT_SUSPENDABLE);

      // Evaluate all suspendable states that belong to a weak accepting and rejecting SCC.
      var alpha = stateFormula.substitute(index -> {
        switch (statusMap.get(index)) {
          case WEAK_ACCEPTING_SUSPENDABLE:
            return Optional.of(PropositionalFormula.trueConstant());

          case WEAK_REJECTING_SUSPENDABLE:
            return Optional.of(PropositionalFormula.falseConstant());

          default:
            return Optional.empty();
        }
      }).normalise();

      {
        var remainingVariables = alpha.variables();
        statusMap.entrySet().forEach(entry -> {
          if (remainingVariables.contains(entry.getKey())) {
            return;
          }

          switch (entry.getValue()) {
            case WEAK_ACCEPTING_SUSPENDABLE:
            case WEAK_REJECTING_SUSPENDABLE:
              // We can still suspend these, because we don't care about their progress.
            case WEAK_ACCEPTING_NOT_SUSPENDABLE:
            case WEAK_REJECTING_NOT_SUSPENDABLE:
              // We have not protected anything yet, override!
            case NOT_SUSPENDABLE:
              entry.setValue(SUSPENDABLE);
              return;

            default:
              // do nothing.
          }
        });
      }

      assert !statusMap.containsValue(WEAK_ACCEPTING_SUSPENDABLE);
      assert !statusMap.containsValue(WEAK_REJECTING_SUSPENDABLE);

      // Iteratively look for states that belong to a weak accepting or rejecting SCC and replace
      // them by a constant, clean-up and repeat.
      while (true) {
        Optional<Map.Entry<Integer, Status>> firstWeakIndex = statusMap.entrySet().stream()
          .filter(entry -> entry.getValue() == WEAK_ACCEPTING_NOT_SUSPENDABLE
            || entry.getValue() == WEAK_REJECTING_NOT_SUSPENDABLE)
          .findFirst();

        if (firstWeakIndex.isEmpty()) {
          break;
        }

        int weakIndex = firstWeakIndex.get().getKey();
        var value =
          Optional.of(firstWeakIndex.get().getValue() == WEAK_ACCEPTING_NOT_SUSPENDABLE
            ? PropositionalFormula.<Integer>trueConstant()
            : PropositionalFormula.<Integer>falseConstant());

        alpha = alpha.substitute(i -> i == weakIndex ? value : Optional.empty()).normalise();

        var remainingVariables = alpha.variables();
        statusMap.entrySet().forEach(entry -> {
          if (remainingVariables.contains(entry.getKey())) {
            return;
          }

          if (entry.getKey() == weakIndex) {
            entry.setValue(NOT_SUSPENDABLE);
            return;
          }

          assert Set.of(
            WEAK_ACCEPTING_NOT_SUSPENDABLE,
            WEAK_REJECTING_NOT_SUSPENDABLE,
            SUSPENDABLE,
            NOT_SUSPENDABLE).contains(entry.getValue());

          if (entry.getValue() != NOT_SUSPENDABLE) {
            entry.setValue(SUSPENDABLE);
          }
        });
      }

      assert Set.of(SUSPENDABLE, NOT_SUSPENDABLE).containsAll(statusMap.values());
      return alpha;
    }

    private PropositionalFormula<Integer> beta(
      Set<Integer> variables, Map<Integer, ? extends BreakpointStateRejecting> stateMap) {

      if (simpleConstruction) {
        return PropositionalFormula.trueConstant();
      }

      List<PropositionalFormula<Integer>> facts = new ArrayList<>();

      for (var pair1 : stateMap.entrySet()) {
        if (!variables.contains(pair1.getKey())) {
          continue;
        }

        for (var pair2 : stateMap.entrySet()) {
          if (!variables.contains(pair2.getKey())) {
            continue;
          }

          if (pair1.getKey().equals(pair2.getKey())) {
            assert pair1.equals(pair2);
            continue;
          }

          if (pair1.getValue().all().implies(pair2.getValue().all())) {
            var fstFact
              = PropositionalFormula.Negation.of(PropositionalFormula.Variable.of(pair1.getKey()));

            var sndFact
              = PropositionalFormula.Variable.of(pair2.getKey());

            facts.add(PropositionalFormula.Disjunction.of(fstFact, sndFact));
          }
        }
      }

      return PropositionalFormula.Conjunction.of(facts);
    }
  }

  // First normalise.

  private static final class NormalFormConverter
    extends PropositionalVisitor<Formula> {

    private final Map<Formula.TemporalOperator, Integer> referenceCounter = new HashMap<>();
    private final boolean simpleConstruction;

    private NormalFormConverter(boolean simpleConstruction) {
      this.simpleConstruction = simpleConstruction;
    }

    @Override
    protected Formula visit(Formula.TemporalOperator formula) {
      // Keep this in sync with initial state constructor.

      if (referenceCounter.containsKey(formula)) {
        referenceCounter.put(formula, 2);
        return formula;
      }

      var formulaNot = (Formula.TemporalOperator) formula.not();

      if (referenceCounter.containsKey(formulaNot)) {
        referenceCounter.put(formulaNot, 2);
        return formula;
      }

      if (SyntacticFragments.isSafetyCoSafety(formula)) {
        referenceCounter.put(formula, 1);
        return formula;
      }

      if (SyntacticFragments.isSafetyCoSafety(formulaNot)) {
        referenceCounter.put(formulaNot, 1);
        return formula;
      }

      var normalForm1 =
        PushNextThroughPropositionalVisitor.apply(
          SimplifierFactory.apply(
            NORMALISATION.apply(formula), SimplifierFactory.Mode.SYNTACTIC_FIXPOINT));

      if (simpleConstruction) {
        return normalForm1.accept(this);
      }

      var normalForm2 =
        PushNextThroughPropositionalVisitor.apply(
          SimplifierFactory.apply(
            DUAL_NORMALISATION.apply(formula), SimplifierFactory.Mode.SYNTACTIC_FIXPOINT));

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

  // Then count occurences.

  // Only merge, if single occurence.

  private static final class InitialStateConstructor
    extends PropositionalVisitor<PropositionalFormula<Integer>> {

    private final SafetyCoSafety dbw;
    private final List<BreakpointStateRejecting> initialStates = new ArrayList<>();
    private final BiMap<Formula, Integer> mapping = HashBiMap.create();
    private final boolean simpleConstruction;
    private final Set<Formula.TemporalOperator> nonUnique;

    public InitialStateConstructor(
      SafetyCoSafety dbw,
      boolean simpleConstruction,
      Set<Formula.TemporalOperator> nonUnique) {

      this.dbw = dbw;
      this.simpleConstruction = simpleConstruction;
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
    public PropositionalFormula<Integer> visit(Conjunction conjunction) {

      List<PropositionalFormula<Integer>> operands = new ArrayList<>(conjunction.operands.size());
      List<Formula> weakOrCoSafetySafety = new ArrayList<>();

      for (Formula operand : conjunction.operands) {
        boolean isWeak = SyntacticFragments.DELTA_1.contains(operand);
        boolean isCoSafetySafetyAndUnique = operand instanceof Formula.TemporalOperator
          && SyntacticFragments.isCoSafetySafety(operand)
          && uniqueReference((Formula.TemporalOperator) operand);

        if (!simpleConstruction && (isWeak || isCoSafetySafetyAndUnique)) {
          weakOrCoSafetySafety.add(operand);
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

        if (!simpleConstruction && (isWeak || isSafetyCoSafetyAndUnique)) {
          weakOrSafetyCoSafety.add(operand);
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

    public abstract ToParityTransformer.ZielonkaTreeRoot zielonkaTreeRoot();

    public abstract ToParityTransformer.Path zielonkaTreePath();

    private static State of(
      PropositionalFormula<Integer> stateFormula,
      Map<Integer, BreakpointStateRejecting> stateMap,
      ToParityTransformer.ZielonkaTreeRoot zielonkaTreeRoot,
      ToParityTransformer.Path zielonkaTreePath,
      Map<Integer, Status> statusMap) {

      assert stateFormula.variables().equals(stateMap.keySet());
      assert stateFormula.variables().equals(statusMap.keySet());
      assert Set.of(SUSPENDABLE, NOT_SUSPENDABLE).containsAll(statusMap.values());

      Set<Integer> toBeSuspended = zielonkaTreeRoot.root().subtree(zielonkaTreePath).colours();

      var suspendedStateMap = new HashMap<Integer, BreakpointStateRejecting>(stateMap.size());

      for (Map.Entry<Integer, BreakpointStateRejecting> entry : stateMap.entrySet()) {
        Integer index = entry.getKey();
        BreakpointStateRejecting state = entry.getValue();
        if (statusMap.get(index) == SUSPENDABLE && toBeSuspended.contains(index)) {
          suspendedStateMap.put(index, state.suspend());
        } else {
          suspendedStateMap.put(index, state);
        }
      }

      return new AutoValue_NormalformDPAConstruction_State(
        stateFormula, Map.copyOf(suspendedStateMap), zielonkaTreeRoot, zielonkaTreePath);
    }
  }

  public enum Status {
    TERMINAL_ACCEPTING,
    TERMINAL_REJECTING,

    TRANSIENT_SUSPENDABLE,
    TRANSIENT_NOT_SUSPENDABLE,

    WEAK_ACCEPTING_SUSPENDABLE,
    WEAK_ACCEPTING_NOT_SUSPENDABLE,

    WEAK_REJECTING_SUSPENDABLE,
    WEAK_REJECTING_NOT_SUSPENDABLE,

    SUSPENDABLE,
    NOT_SUSPENDABLE
  }
}
