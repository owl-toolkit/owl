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

import com.google.common.base.Preconditions;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.edge.Edge;
import owl.collections.ValuationSet;
import owl.ltl.EquivalenceClass;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dra.LTL2DRAFunction;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;

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
      return safety(formula);
    }

    if (allowedConstructions.contains(Constructions.CO_SAFETY)
      && SyntacticFragment.CO_SAFETY.contains(formula)) {
      return coSafety(formula);
    }

    if (allowedConstructions.contains(Constructions.BUCHI)
      && SyntacticFragments.isDetBuchiRecognisable(formula.formula())) {
      return buchi(formula);
    }

    if (allowedConstructions.contains(Constructions.CO_BUCHI)
      && SyntacticFragments.isDetCoBuchiRecognisable(formula.formula())) {
      return coBuchi(formula);
    }

    if (fallback == null) {
      throw new IllegalArgumentException("All allowed constructions exhausted.");
    }

    return fallback.apply(formula);
  }

  private Automaton<DegeneralizedBreakpointState, BuchiAcceptance> buchi(LabelledFormula formula) {
    EnumSet<Configuration> configuration = EnumSet.of(
      Configuration.EAGER_UNFOLD,
      Configuration.SUPPRESS_JUMPS,
      Configuration.FORCE_JUMPS,
      Configuration.OPTIMISED_STATE_STRUCTURE);

    var builder = LTL2LDBAFunction
      .createDegeneralizedBreakpointLDBABuilder(environment, configuration);
    var ldba = builder.apply(formula);
    var acceptingComponent = ldba.acceptingComponent();

    assert ldba.isDeterministic();

    if (acceptingComponent.initialStates().isEmpty()) {
      var vsFactory = environment.factorySupplier().getValuationSetFactory(formula.variables());
      return AutomatonFactory.singleton(vsFactory, DegeneralizedBreakpointState.createSink(),
        BuchiAcceptance.INSTANCE);
    }

    return ldba.acceptingComponent();
  }

  private Automaton<DegeneralizedBreakpointState, CoBuchiAcceptance> coBuchi(
    LabelledFormula formula) {
    return AutomatonUtil.cast(Views.complement(buchi(formula.not()),
      DegeneralizedBreakpointState.createSink()), CoBuchiAcceptance.class);
  }

  private Automaton<EquivalenceClass, BuchiAcceptance> coSafety(LabelledFormula formula) {
    Preconditions.checkArgument(SyntacticFragment.CO_SAFETY.contains(formula.formula()),
      "Formula is not from the syntactic co-safety fragment.");

    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory(factories, true, false);

    BiFunction<EquivalenceClass, BitSet, Set<Edge<EquivalenceClass>>> single = (x, y) -> {
      var successor = factory.getSuccessor(x, y);

      if (successor.isFalse()) {
        return Set.of();
      }

      if (successor.isTrue()) {
        return Set.of(Edge.of(successor, 0));
      }

      return Set.of(Edge.of(successor));
    };

    Function<EquivalenceClass, Map<Edge<EquivalenceClass>, ValuationSet>> bulk = x ->
      factory.getEdges(x, y -> y.isTrue() ?  OptionalInt.of(0) : OptionalInt.empty());

    return AutomatonFactory.create(factories.vsFactory, factory.getInitial(formula.formula()),
      BuchiAcceptance.INSTANCE, single, bulk);
  }

  private Automaton<EquivalenceClass, AllAcceptance> safety(LabelledFormula formula) {
    Preconditions.checkArgument(SyntacticFragment.SAFETY.contains(formula.formula()),
      "Formula is not from the syntactic safety fragment.");

    var factories = environment.factorySupplier().getFactories(formula.variables(), false);
    var factory = new EquivalenceClassStateFactory(factories, true, false);

    BiFunction<EquivalenceClass, BitSet, Set<Edge<EquivalenceClass>>> single = (x, y) -> {
      var successor = factory.getSuccessor(x, y);
      return successor.isFalse() ? Set.of() : Set.of(Edge.of(successor));
    };

    return AutomatonFactory.create(factories.vsFactory, factory.getInitial(formula.formula()),
      AllAcceptance.INSTANCE, single, factory::getEdges);
  }
}
