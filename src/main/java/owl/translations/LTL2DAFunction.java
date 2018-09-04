/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations;

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.GREEDY;
import static owl.translations.ltl2dpa.LTL2DPAFunction.RECOMMENDED_ASYMMETRIC_CONFIG;
import static owl.translations.ltl2dra.LTL2DRAFunction.Configuration.EXISTS_SAFETY_CORE;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.ImplicitNonDeterministicEdgeTreeAutomaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.collections.ValuationTree;
import owl.factories.ValuationSetFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.XOperator;
import owl.run.Environment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.LTL2DRAFunction;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;

public final class LTL2DAFunction implements Function<LabelledFormula, Automaton<?, ?>> {
  private final Environment environment;
  private final EnumSet<Constructions> allowedConstructions;

  @Nullable
  private final Function<LabelledFormula, ? extends Automaton<?, ?>> fallback;

  public LTL2DAFunction(Environment environment, boolean onTheFly,
    EnumSet<Constructions> allowedConstructions) {
    this.allowedConstructions = EnumSet.copyOf(allowedConstructions);
    this.environment = environment;

    if (this.allowedConstructions.contains(Constructions.EMERSON_LEI)) {
      fallback = new DelagBuilder<>(environment);
    } else if (this.allowedConstructions.contains(Constructions.RABIN)) {
      var configuration = EnumSet.of(LTL2DRAFunction.Configuration.OPTIMISE_INITIAL_STATE,
        LTL2DRAFunction.Configuration.OPTIMISED_STATE_STRUCTURE, EXISTS_SAFETY_CORE);
      fallback = new LTL2DRAFunction(environment, configuration);
    } else if (this.allowedConstructions.contains(Constructions.PARITY)) {
      var configuration = EnumSet.copyOf(RECOMMENDED_ASYMMETRIC_CONFIG);

      if (onTheFly) {
        configuration.add(GREEDY);
        configuration.remove(COMPRESS_COLOURS);
        configuration.remove(LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE);
      }

      fallback = new LTL2DPAFunction(environment, configuration);
    } else {
      fallback = null;
    }
  }

  public enum Constructions {
    SAFETY, CO_SAFETY, BUCHI, CO_BUCHI, EMERSON_LEI, RABIN, PARITY;
  }

  @Override
  public Automaton<?, ?> apply(LabelledFormula formula) {
    if (allowedConstructions.contains(Constructions.SAFETY)
      && SyntacticFragment.SAFETY.contains(formula)) {
      return safety(environment, formula);
    }

    if (allowedConstructions.contains(Constructions.CO_SAFETY)
      && SyntacticFragment.CO_SAFETY.contains(formula)) {
      return coSafety(environment, formula);
    }

    if (formula.formula() instanceof XOperator) {
      return delay(formula);
    }

    if (allowedConstructions.contains(Constructions.BUCHI)) {
      if (SyntacticFragments.isGfCoSafety(formula.formula())) {
        return gfCoSafety(environment, formula);
      }

      if (SyntacticFragments.isGCoSafety(formula.formula())) {
        return gCoSafety(environment, formula);
      }
    }

    if (allowedConstructions.contains(Constructions.CO_BUCHI)) {
      if (SyntacticFragments.isFgSafety(formula.formula())) {
        return fgSafety(environment, formula);
      }

      if (SyntacticFragments.isDetCoBuchiRecognisable(formula.formula())) {
        return fSafety(environment, formula);
      }
    }

    if (fallback == null) {
      throw new IllegalArgumentException("All allowed constructions exhausted.");
    }

    return fallback.apply(formula);
  }

  private Automaton<Object, ?> delay(LabelledFormula formula) {
    var automaton =
      AutomatonUtil.cast(apply(formula.wrap(((XOperator) formula.formula()).operand)));

    return new Automaton<>() {
      private final Object initialState = new Object();
      private final Set<Edge<Object>> initialStateEdges =
        automaton.initialStates().stream().map(Edge::of).collect(Collectors.toUnmodifiableSet());

      @Override
      public OmegaAcceptance acceptance() {
        return automaton.acceptance();
      }

      @Override
      public ValuationSetFactory factory() {
        return automaton.factory();
      }

      @Override
      public Set<Object> initialStates() {
        return Set.of(initialState);
      }

      @Override
      public Set<Object> states() {
        return Sets.union(initialStates(), automaton.states());
      }

      @Override
      public Set<Edge<Object>> edges(Object state, BitSet valuation) {
        return initialState.equals(state) ? initialStateEdges : automaton.edges(state, valuation);
      }

      @Override
      public Set<Edge<Object>> edges(Object state) {
        return initialState.equals(state) ? initialStateEdges : automaton.edges(state);
      }

      @Override
      public Map<Edge<Object>, ValuationSet> edgeMap(Object state) {
        return initialState.equals(state)
          ? Maps.toMap(initialStateEdges, x -> factory().universe())
          : automaton.edgeMap(state);
      }

      @Override
      public ValuationTree<Edge<Object>> edgeTree(Object state) {
        return initialState.equals(state)
          ? ValuationTree.of(initialStateEdges)
          : automaton.edgeTree(state);
      }

      @Override
      public List<PreferredEdgeAccess> preferredEdgeAccess() {
        return automaton.preferredEdgeAccess();
      }
    };
  }

  static Automaton<EquivalenceClass, BuchiAcceptance> coSafety(
    Environment environment,
    LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory.CoSafety(factories.eqFactory, true,
      formula.formula());
    return new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory,
      Set.of(factory.initialState()), BuchiAcceptance.INSTANCE, factory::edges, factory::edgeTree);
  }

  static Automaton<EquivalenceClass, CoBuchiAcceptance> fgSafety(
    Environment environment,
    LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory.FgSafety(factories.eqFactory, true,
      formula.formula());
    return new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory, Set.of(factory
      .steppedInitialState()), CoBuchiAcceptance.INSTANCE, factory::edges, factory::edgeTree);
  }

  static Automaton<EquivalenceClass, BuchiAcceptance> gfCoSafety(
    Environment environment,
    LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory.GfCoSafety(factories.eqFactory, true,
      formula.formula());
    return new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory, Set.of(factory
      .steppedInitialState()), BuchiAcceptance.INSTANCE, factory::edges, factory::edgeTree);
  }

  static Automaton<EquivalenceClassStateFactory.BreakpointState, BuchiAcceptance> gCoSafety(
    Environment environment,
    LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), true);
    var factory = new EquivalenceClassStateFactory.GCoSafety(factories.eqFactory, true,
      formula.formula());
    return new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory, Set.of(factory
      .initialState()), BuchiAcceptance.INSTANCE, factory::edges, factory::edgeTree);
  }

  static Automaton<EquivalenceClassStateFactory.BreakpointState, CoBuchiAcceptance> fSafety(
    Environment environment,
    LabelledFormula formula) {
    var automaton = gCoSafety(environment, formula.not());
    var factory = automaton.onlyInitialState().current().factory();
    var complementAutomaton = Views.complement(automaton,
      EquivalenceClassStateFactory.BreakpointState.of(factory.getFalse(), factory.getFalse()));
    return AutomatonUtil.cast(complementAutomaton, CoBuchiAcceptance.class);
  }

  static Automaton<EquivalenceClass, AllAcceptance> safety(
    Environment environment,
    LabelledFormula formula) {
    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory.Safety(factories.eqFactory, true,
      formula.formula());
    return new ImplicitNonDeterministicEdgeTreeAutomaton<>(factories.vsFactory,
      Set.of(factory.initialState()), AllAcceptance.INSTANCE, factory::edges, factory::edgeTree);
  }
}
