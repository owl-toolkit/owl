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
import java.util.Collections;
import java.util.EnumSet;
import java.util.function.Function;
import java.util.logging.Logger;
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.JumpAnalysisResult.TYPE;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedAcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.GObligations;
import owl.translations.ltl2ldba.breakpoint.GObligationsJumpFactory;
import owl.translations.ltl2ldba.breakpoint.GeneralizedAcceptingComponentBuilder;
import owl.translations.ltl2ldba.breakpoint.GeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.translations.ltl2ldba.breakpointfree.FGObligationsSelector;
import owl.translations.ltl2ldba.breakpointfree.GeneralizedBreakpointFreeState;

public final class LTL2LDBAFunction<S, B extends GeneralizedBuchiAcceptance, C extends
  RecurringObligation> implements Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
  S, B, C>> {

  public static Logger LOGGER = Logger.getLogger("ltl2ldba");

  private final Function<Factories, ExploreBuilder<Jump<C>, S, B>> builderConstructor;
  private final Function<Formula, Formula> formulaPreprocessor;
  private final Function<S, C> getAnnotation;
  private final EnumSet<Optimisation> optimisations;
  private final boolean deterministicInitialComponent;
  private final Function<EquivalenceClass, JumpFactory<C>> selectorConstructor;

  private LTL2LDBAFunction(
    Function<Formula, Formula> formulaPreprocessor,
    Function<EquivalenceClass, JumpFactory<C>> selectorConstructor,
    Function<Factories, ExploreBuilder<Jump<C>, S, B>> builderConstructor,
    EnumSet<Optimisation> optimisations, Function<S, C> getAnnotation) {
    this.formulaPreprocessor = formulaPreprocessor;
    this.selectorConstructor = selectorConstructor;
    this.builderConstructor = builderConstructor;
    this.optimisations = EnumSet.copyOf(optimisations);
    this.getAnnotation = getAnnotation;
    this.deterministicInitialComponent = optimisations
      .contains(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>>
  createDegeneralizedBreakpointFreeLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcess,
    x -> FGObligationsSelector.build(x, optimisations),
    x -> new owl.translations.ltl2ldba.breakpointfree.DegeneralizedAcceptingComponentBuilder(x,
        optimisations),
      optimisations,
      DegeneralizedBreakpointFreeState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>>
  createDegeneralizedBreakpointLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcessPushX,
    x -> GObligationsJumpFactory.build(x, EnumSet.copyOf(optimisations)),
    x -> new DegeneralizedAcceptingComponentBuilder(x, ImmutableSet.copyOf(optimisations)),
      optimisations,
      DegeneralizedBreakpointState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations>>
  createGeneralizedBreakpointFreeLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcess,
    x -> FGObligationsSelector.build(x, optimisations),
    x -> new owl.translations.ltl2ldba.breakpointfree.GeneralizedAcceptingComponentBuilder(x,
        optimisations),
      optimisations,
      GeneralizedBreakpointFreeState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>>
  createGeneralizedBreakpointLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcessPushX,
    x -> GObligationsJumpFactory.build(x, EnumSet.copyOf(optimisations)),
    x -> new GeneralizedAcceptingComponentBuilder(x, EnumSet.copyOf(optimisations)),
      optimisations,
      GeneralizedBreakpointState::getObligations);
  }

  private static Formula preProcessPushX(Formula formula) {
    return RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, preProcess(formula));
  }

  private static Formula preProcess(Formula formula) {
    return RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
  }

  @Override
  public LimitDeterministicAutomaton<EquivalenceClass, S, B, C> apply(Formula formula) {
    Formula processedFormula = formulaPreprocessor.apply(formula);
    Factories factories = Registry.getFactories(processedFormula);

    JumpFactory<C> factory = selectorConstructor.apply(factories.equivalenceClassFactory
      .createEquivalenceClass(processedFormula));

    LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
      builder = createBuilder(factories, factory);

    for (EquivalenceClass initialClass : createInitialClasses(factories, processedFormula)) {
      JumpAnalysisResult<C> obligations = factory.getAvailableJumps(initialClass);

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

    initialComponent.remapAcceptance((state, x) -> {
      assert !state.isFalse();
      return state.testSupport(Fragments::isSafety) ? bitSet : null;
    });

    return ldba;
  }

  private LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
  createBuilder(Factories factories, JumpFactory<C> selector) {
    ExploreBuilder<Jump<C>, S, B> acceptingComponentBuilder = builderConstructor.apply(factories);
    InitialComponentBuilder<C> initialComponentBuilder = new InitialComponentBuilder<>(factories,
      EnumSet.copyOf(optimisations), selector);

    return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder,
      initialComponentBuilder::getJumps,
      getAnnotation,
      EnumSet.copyOf(optimisations),
      x -> x.testSupport(Fragments::isSafety));
  }

  private Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(
      factories.equivalenceClassFactory, EnumSet.copyOf(optimisations));

    if (deterministicInitialComponent) {
      return Collections.singleton(factory.getInitial(formula));
    }

    EquivalenceClass clazz = factories.equivalenceClassFactory.createEquivalenceClass(formula);
    return factory.splitEquivalenceClass(clazz);
  }
}
