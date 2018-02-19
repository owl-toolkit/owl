/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
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
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.Environment;
import owl.translations.ltl2ldba.AnalysisResult.TYPE;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpManager;
import owl.translations.ltl2ldba.breakpoint.GeneralizedAcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpoint.GeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedAcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsJumpManager;
import owl.translations.ltl2ldba.breakpointfree.GeneralizedBreakpointFreeState;

public final class
LTL2LDBAFunction<S, B extends GeneralizedBuchiAcceptance, C extends RecurringObligation>
  implements Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass, S, B, C>> {
  private final Function<Factories, MutableAutomatonBuilder<Jump<C>, S, B>> builderConstructor;
  private final Environment env;
  private final Function<S, C> getAnnotation;
  private final ImmutableSet<Configuration> configuration;
  private final Function<EquivalenceClass, AbstractJumpManager<C>> selectorConstructor;

  private LTL2LDBAFunction(Environment env,
    Function<EquivalenceClass, AbstractJumpManager<C>> selectorConstructor,
    Function<Factories, MutableAutomatonBuilder<Jump<C>, S, B>> builderConstructor,
    ImmutableSet<Configuration> configuration, Function<S, C> getAnnotation) {
    this.env = env;
    this.selectorConstructor = selectorConstructor;
    this.builderConstructor = builderConstructor;
    this.configuration = configuration;
    this.getAnnotation = getAnnotation;
  }

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>>
  createDegeneralizedBreakpointFreeLDBABuilder(Environment env,
    Set<Configuration> configuration) {
    ImmutableSet<Configuration> configuration2 = ImmutableSet.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      x -> FGObligationsJumpManager.build(x, configuration2),
      x -> new DegeneralizedAcceptingComponentBuilder(x, configuration2),
      configuration2, DegeneralizedBreakpointFreeState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>>
  createDegeneralizedBreakpointLDBABuilder(Environment env, Set<Configuration> configuration) {
    ImmutableSet<Configuration> configuration2 = ImmutableSet.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      x -> GObligationsJumpManager.build(x, configuration2),
      x -> new owl.translations.ltl2ldba.breakpoint.DegeneralizedAcceptingComponentBuilder(x,
        configuration2), configuration2, DegeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations>>
  createGeneralizedBreakpointFreeLDBABuilder(Environment env, Set<Configuration> configuration) {
    ImmutableSet<Configuration> configuration2 = ImmutableSet.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      x -> FGObligationsJumpManager.build(x, configuration2),
      x -> new owl.translations.ltl2ldba.breakpointfree.GeneralizedAcceptingComponentBuilder(x,
        configuration2),
      configuration2,
      GeneralizedBreakpointFreeState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>>
  createGeneralizedBreakpointLDBABuilder(Environment env, Set<Configuration> configuration) {
    ImmutableSet<Configuration> configuration2 = ImmutableSet.copyOf(configuration);
    return new LTL2LDBAFunction<>(env,
      x -> GObligationsJumpManager.build(x, configuration2),
      x -> new GeneralizedAcceptingComponentBuilder(x, configuration2),
      configuration2,
      GeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  @Override
  public LimitDeterministicAutomaton<EquivalenceClass, S, B, C> apply(LabelledFormula formula) {
    LabelledFormula rewritten = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    Factories factories = env.factorySupplier().getFactories(rewritten);

    Formula processedFormula = rewritten.formula;
    AbstractJumpManager<C> factory = selectorConstructor.apply(factories.eqFactory
      .of(processedFormula));

    LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
      builder = createBuilder(factories, factory);

    for (EquivalenceClass initialClass : createInitialClasses(factories, processedFormula)) {
      AnalysisResult<C> obligations = factory.analyse(initialClass);

      if (obligations.type == TYPE.MUST) {
        builder.addAccepting(Iterables.getOnlyElement(obligations.jumps));
      } else {
        builder.addInitial(initialClass);
      }
    }

    LimitDeterministicAutomaton<EquivalenceClass, S, B, C> ldba = builder.build();

    // HACK:
    //
    // Since we have some states in the initial component that are accepting but adding jumps
    // increases the size of the automaton, we just remap this acceptance. This needs to be solved
    // more cleanly!

    BitSet bitSet = new BitSet();
    bitSet.set(0, ldba.getAcceptingComponent().getAcceptance().size);

    MutableAutomaton<EquivalenceClass, NoneAcceptance> initialComponent =
      (MutableAutomaton<EquivalenceClass, NoneAcceptance>) ldba.getInitialComponent();

    initialComponent.updateEdges((state, x) -> {
      assert !state.isFalse();
      return state.testSupport(Fragments::isSafety) ? x.withAcceptance(bitSet) : x;
    });
    return ldba;
  }

  private LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
  createBuilder(Factories factories, AbstractJumpManager<C> selector) {
    MutableAutomatonBuilder<Jump<C>, S, B> acceptingComponentBuilder = builderConstructor
      .apply(factories);
    InitialComponentBuilder<C> initialComponentBuilder = new InitialComponentBuilder<>(factories,
      configuration, selector);

    Predicate<EquivalenceClass> isSafety = x -> x.testSupport(Fragments::isSafety);

    if (configuration.contains(Configuration.EPSILON_TRANSITIONS)) {
      return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
        acceptingComponentBuilder,
        initialComponentBuilder::getJumps,
        getAnnotation,
        EnumSet.of(
          LimitDeterministicAutomatonBuilder.Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES),
        isSafety);
    } else {
      return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
        acceptingComponentBuilder,
        initialComponentBuilder::getJumps,
        getAnnotation,
        EnumSet.of(
          LimitDeterministicAutomatonBuilder.Configuration.REMOVE_EPSILON_TRANSITIONS,
          LimitDeterministicAutomatonBuilder.Configuration.SUPPRESS_JUMPS_FOR_TRANSIENT_STATES),
        isSafety);
    }
  }

  private Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(
      factories.eqFactory, configuration);

    if (configuration.contains(Configuration.NON_DETERMINISTIC_INITIAL_COMPONENT)) {
      EquivalenceClass clazz = factories.eqFactory.of(formula);
      return factory.splitEquivalenceClass(clazz);
    }

    return Set.of(factory.getInitial(formula));
  }

  public enum Configuration {
    NON_DETERMINISTIC_INITIAL_COMPONENT, EAGER_UNFOLD, FORCE_JUMPS, OPTIMISED_STATE_STRUCTURE,
    SUPPRESS_JUMPS, EPSILON_TRANSITIONS
  }
}
