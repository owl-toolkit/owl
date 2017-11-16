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
import owl.automaton.ExploreBuilder;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.NoneAcceptance;
import owl.automaton.edge.Edges;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.factories.Factories;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.run.env.Environment;
import owl.translations.Optimisation;
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
  private final Function<Factories, ExploreBuilder<Jump<C>, S, B>> builderConstructor;
  private final boolean deterministicInitialComponent;
  private final Environment env;
  private final Function<LabelledFormula, LabelledFormula> formulaPreprocessor;
  private final Function<S, C> getAnnotation;
  private final EnumSet<Optimisation> optimisations;
  private final Function<EquivalenceClass, AbstractJumpManager<C>> selectorConstructor;

  private LTL2LDBAFunction(Environment env,
    Function<LabelledFormula, LabelledFormula> formulaPreprocessor,
    Function<EquivalenceClass, AbstractJumpManager<C>> selectorConstructor,
    Function<Factories, ExploreBuilder<Jump<C>, S, B>> builderConstructor,
    EnumSet<Optimisation> optimisations, Function<S, C> getAnnotation) {
    this.env = env;
    this.formulaPreprocessor = formulaPreprocessor;
    this.selectorConstructor = selectorConstructor;
    this.builderConstructor = builderConstructor;
    this.optimisations = EnumSet.copyOf(optimisations);
    this.getAnnotation = getAnnotation;
    this.deterministicInitialComponent =
      optimisations.contains(Optimisation.DETERMINISTIC_INITIAL_COMPONENT);
  }

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>>
  createDegeneralizedBreakpointFreeLDBABuilder(Environment env,
    EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(env, LTL2LDBAFunction::preProcess,
      x -> FGObligationsJumpManager.build(x, optimisations),
      x -> new DegeneralizedAcceptingComponentBuilder(x, optimisations),
      optimisations, DegeneralizedBreakpointFreeState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, GObligations>>
  createDegeneralizedBreakpointLDBABuilder(Environment env, EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(env, LTL2LDBAFunction::preProcess,
      x -> GObligationsJumpManager.build(x, EnumSet.copyOf(optimisations)),
      x -> new owl.translations.ltl2ldba.breakpoint.DegeneralizedAcceptingComponentBuilder(x,
        ImmutableSet.copyOf(optimisations)),
      optimisations,
      DegeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations>>
  createGeneralizedBreakpointFreeLDBABuilder(Environment env, EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(env, LTL2LDBAFunction::preProcess,
      x -> FGObligationsJumpManager.build(x, optimisations),
      x -> new owl.translations.ltl2ldba.breakpointfree.GeneralizedAcceptingComponentBuilder(x,
        optimisations),
      optimisations,
      GeneralizedBreakpointFreeState::getObligations);
  }
  // CSON: Indentation

  // CSOFF: Indentation
  public static Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, GObligations>>
  createGeneralizedBreakpointLDBABuilder(Environment env, EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(env, LTL2LDBAFunction::preProcess,
      x -> GObligationsJumpManager.build(x, EnumSet.copyOf(optimisations)),
      x -> new GeneralizedAcceptingComponentBuilder(x, EnumSet.copyOf(optimisations)),
      optimisations,
      GeneralizedBreakpointState::getObligations);
  }
  // CSON: Indentation

  private static LabelledFormula preProcess(LabelledFormula formula) {
    return RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
  }

  @Override
  public LimitDeterministicAutomaton<EquivalenceClass, S, B, C> apply(LabelledFormula formula) {
    LabelledFormula rewritten = formulaPreprocessor.apply(formula);
    Factories factories = env.factorySupplier().getFactories(rewritten);

    Formula processedFormula = rewritten.formula;
    AbstractJumpManager<C> factory = selectorConstructor.apply(factories.eqFactory
      .createEquivalenceClass(processedFormula));

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

    initialComponent.remapEdges((state, x) -> {
      assert !state.isFalse();
      return state.testSupport(Fragments::isSafety) ? Edges.create(x.getSuccessor(), bitSet) : x;
    });
    return ldba;
  }

  private LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
  createBuilder(Factories factories, AbstractJumpManager<C> selector) {
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
      factories.eqFactory, EnumSet.copyOf(optimisations));

    if (deterministicInitialComponent) {
      return Collections.singleton(factory.getInitial(formula));
    }

    EquivalenceClass clazz = factories.eqFactory.createEquivalenceClass(formula);
    return factory.splitEquivalenceClass(clazz);
  }
}
