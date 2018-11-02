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

package owl.translations.ltl2ldba;

import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.automaton.ldba.MutableAutomatonBuilder;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.run.Environment;
import owl.translations.canonical.LegacyFactory;
import owl.translations.ltl2ldba.AnalysisResult.TYPE;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpManager;
import owl.translations.ltl2ldba.breakpoint.GeneralizedAcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpoint.GeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpointfree.AcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpointfree.AcceptingComponentState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsJumpManager;

public final class
LTL2LDBAFunction<S, B extends GeneralizedBuchiAcceptance, C extends RecurringObligation>
  implements Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass, S, B, C>> {
  private final Function<Factories, MutableAutomatonBuilder<Jump<C>, S, B>> builderConstructor;
  private final Environment env;
  private final Function<S, C> getAnnotation;
  private final Set<Configuration> configuration;
  private final BiFunction<Formula, Factories, AbstractJumpManager<C>>
    selectorConstructor;

  private LTL2LDBAFunction(Environment env,
    BiFunction<Formula, Factories, AbstractJumpManager<C>> selectorConstructor,
    Function<Factories, MutableAutomatonBuilder<Jump<C>, S, B>> builderConstructor,
    Set<Configuration> configuration, Function<S, C> getAnnotation) {
    this.env = env;
    this.selectorConstructor = selectorConstructor;
    this.builderConstructor = builderConstructor;
    this.configuration = configuration;
    this.getAnnotation = getAnnotation;
  }

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    AcceptingComponentState, BuchiAcceptance, FGObligations>>
  createDegeneralizedBreakpointFreeLDBABuilder(Environment env,
    Set<Configuration> configuration) {
    Set<Configuration> configuration2 = Set.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      (x, y) -> FGObligationsJumpManager.build(x, y, configuration2, false),
      x -> new AcceptingComponentBuilder.Buchi(x, configuration2),
      configuration2, AcceptingComponentState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>>
  createDegeneralizedBreakpointLDBABuilder(Environment env, Set<Configuration> configuration) {
    Set<Configuration> configuration2 = Set.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      (x, y) -> GObligationsJumpManager.build(x, y, configuration2),
      x -> new owl.translations.ltl2ldba.breakpoint.DegeneralizedAcceptingComponentBuilder(x,
        configuration2), configuration2, DegeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    AcceptingComponentState, GeneralizedBuchiAcceptance, FGObligations>>
  createGeneralizedBreakpointFreeLDBABuilder(Environment env, Set<Configuration> configuration) {
    Set<Configuration> configuration2 = Set.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      (x, y) -> FGObligationsJumpManager.build(x, y, configuration2, true),
      x -> new AcceptingComponentBuilder.GeneralizedBuchi(x, configuration2),
      configuration2,
      AcceptingComponentState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>>
  createGeneralizedBreakpointLDBABuilder(Environment env, Set<Configuration> configuration) {
    Set<Configuration> configuration2 = Set.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      (x, y) -> GObligationsJumpManager.build(x, y, configuration2),
      x -> new GeneralizedAcceptingComponentBuilder(x, configuration2),
      configuration2,
      GeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  @Override
  public LimitDeterministicAutomaton<EquivalenceClass, S, B, C> apply(LabelledFormula input) {
    LabelledFormula formula = SyntacticFragments.normalize(input, SyntacticFragment.NNF);

    var factories = env.factorySupplier().getFactories(formula.variables(), true);
    var jumpManager = selectorConstructor.apply(formula.formula(), factories);
    var initialComponentBuilder = new InitialComponentBuilder<>(factories, configuration,
      jumpManager);
    var acceptingComponentBuilder = builderConstructor.apply(factories);

    LimitDeterministicAutomatonBuilder<EquivalenceClass, Jump<C>, S, B, C> builder;

    if (configuration.contains(Configuration.EPSILON_TRANSITIONS)) {
      builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder::build,
        acceptingComponentBuilder,
        initialComponentBuilder::getJumps,
        getAnnotation,
        EnumSet.of(
          LimitDeterministicAutomatonBuilder.Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES),
        x1 -> SafetyDetector.hasSafetyCore(x1, false));
    } else {
      builder = LimitDeterministicAutomatonBuilder.create(initialComponentBuilder::build,
        acceptingComponentBuilder,
        initialComponentBuilder::getJumps,
        getAnnotation,
        EnumSet.of(
          LimitDeterministicAutomatonBuilder.Configuration.REMOVE_EPSILON_TRANSITIONS,
          LimitDeterministicAutomatonBuilder.Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES),
        x1 -> SafetyDetector.hasSafetyCore(x1, false));
    }

    var factory = new LegacyFactory(factories, configuration);
    var initialClass = factory.initialStateInternal(factories.eqFactory.of(formula.formula()));
    var obligations = jumpManager.analyse(initialClass);

    if (obligations.type == TYPE.MUST) {
      builder.addInitialStateAcceptingComponent(Iterables.getOnlyElement(obligations.jumps));
    } else {
      initialComponentBuilder.add(initialClass);
    }

    var ldba = builder.build();

    // HACK:
    //
    // Since we have some states in the initial component that are accepting but adding jumps
    // increases the size of the automaton, we just remap this acceptance. This needs to be solved
    // more cleanly!

    BitSet bitSet = new BitSet();
    bitSet.set(0, ldba.acceptingComponent().acceptance().size);

    MutableAutomaton<EquivalenceClass, NoneAcceptance> initialComponent =
      (MutableAutomaton<EquivalenceClass, NoneAcceptance>) ldba.initialComponent();

    initialComponent.updateEdges((state, x) -> {
      assert !state.isFalse();
      return SafetyDetector.hasSafetyCore(state, false) ? x.withAcceptance(bitSet) : x;
    });

    initialComponent.trim();

    return ldba;
  }

  public enum Configuration {
    EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE, SUPPRESS_JUMPS, EPSILON_TRANSITIONS
  }
}
