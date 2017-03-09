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

import com.google.common.collect.Iterables;
import java.util.BitSet;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import owl.automaton.ExploreBuilder;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.ldba.LimitDeterministicAutomatonBuilder;
import owl.factories.EquivalenceClassFactory;
import owl.factories.Factories;
import owl.factories.Registry;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.WOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.UnabbreviateVisitor;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.ng.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.ng.GeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.ng.RecurringObligations2;
import owl.translations.ltl2ldba.ng.RecurringObligations2Selector;

public final class LTL2LDBAFunction<S, B extends GeneralizedBuchiAcceptance, C> implements
  Function<Formula, LimitDeterministicAutomaton<EquivalenceClass, S, B, C>> {

  private final BiFunction<Factories, EnumSet<Optimisation>,
    ExploreBuilder<Jump<C>, S, B>> builderConstructor;
  private final Function<Formula, Formula> formulaPreprocessor;
  private final Function<S, C> getAnnotation;
  private final EnumSet<Optimisation> optimisations;
  private final BiFunction<EquivalenceClassFactory,
    EnumSet<Optimisation>, JumpSelector<C>> selectorConstructor;

  private LTL2LDBAFunction(
    Function<Formula, Formula> formulaPreprocessor,
    BiFunction<EquivalenceClassFactory, EnumSet<Optimisation>, JumpSelector<C>> selectorConstructor,
    BiFunction<Factories, EnumSet<Optimisation>,
      ExploreBuilder<Jump<C>, S, B>> builderConstructor,
    EnumSet<Optimisation> optimisations, Function<S, C> getAnnotation) {
    this.formulaPreprocessor = formulaPreprocessor;
    this.selectorConstructor = selectorConstructor;
    this.builderConstructor = builderConstructor;
    this.optimisations = EnumSet.copyOf(optimisations);
    this.getAnnotation = getAnnotation;
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, RecurringObligations2>>
    createDegeneralizedBreakpointFreeLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::removeMpreProcess,
      RecurringObligations2Selector::new,
      owl.translations.ltl2ldba.ng.DegeneralizedAcceptingComponentBuilder::new,
      optimisations,
      DegeneralizedBreakpointFreeState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointState, BuchiAcceptance, RecurringObligations>>
    createDegeneralizedBreakpointLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcess,
      RecurringObligationsSelector::new,
      DegeneralizedAcceptingComponentBuilder::new,
      optimisations,
      DegeneralizedBreakpointState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, RecurringObligations2>>
    createGeneralizedBreakpointFreeLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::removeMpreProcess,
      RecurringObligations2Selector::new,
      owl.translations.ltl2ldba.ng.GeneralizedAcceptingComponentBuilder::new,
      optimisations,
      GeneralizedBreakpointFreeState::getObligations);
  }

  public static Function<Formula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointState, GeneralizedBuchiAcceptance, RecurringObligations>>
    createGeneralizedBreakpointLDBABuilder(EnumSet<Optimisation> optimisations) {
    return new LTL2LDBAFunction<>(LTL2LDBAFunction::preProcess,
      RecurringObligationsSelector::new,
      GeneralizedAcceptingComponentBuilder::new,
      optimisations,
      GeneralizedBreakpointState::getObligations);
  }

  private static Formula preProcess(Formula formula) {
    Formula processedFormula = RewriterFactory.apply(RewriterEnum.MODAL_ITERATIVE, formula);
    return RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, processedFormula);
  }

  private static Formula removeMpreProcess(Formula formula) {
    return preProcess(preProcess(formula).accept(new UnabbreviateVisitor(
      ROperator.class, WOperator.class, MOperator.class)));
  }

  @Override
  public LimitDeterministicAutomaton<EquivalenceClass, S, B, C> apply(Formula formula) {
    Formula processedFormula = formulaPreprocessor.apply(formula);
    Factories factories = Registry.getFactories(processedFormula);

    JumpSelector<C> selector = selectorConstructor
      .apply(factories.equivalenceClassFactory, EnumSet.copyOf(optimisations));

    LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B, C>
      builder = createBuilder(factories, selector);

    for (EquivalenceClass initialClass : createInitialClasses(factories, processedFormula)) {
      Set<C> obligations = selector.select(initialClass, true);

      if (obligations.size() == 1 && Iterables.getOnlyElement(obligations) != null) {
        builder.addAccepting(new Jump<>(initialClass, Iterables.getOnlyElement(obligations)));
      } else {
        builder.addInitial(initialClass);
      }
    }

    LimitDeterministicAutomaton<EquivalenceClass, S, B, C> ldba = builder.build();

    // Post-process: Remap acceptance to generalized Büchi size.
    BitSet bitSet = new BitSet();
    bitSet.set(0, ldba.getAcceptingComponent().getAcceptance().size);
    ldba.getInitialComponent().remapAcceptance((state, x) -> {
      if (state.isTrue()) {
        return bitSet;
      }

      return null;
    });

    return ldba;
  }

  private LimitDeterministicAutomatonBuilder<EquivalenceClass, EquivalenceClass, Jump<C>, S, B,
    C> createBuilder(
    Factories factories, JumpSelector<C> selector) {
    ExploreBuilder<Jump<C>, S, B> acceptingComponentBuilder = builderConstructor
      .apply(factories, EnumSet.copyOf(optimisations));
    InitialComponentBuilder<C> initialComponentBuilder = new InitialComponentBuilder<>(factories,
      EnumSet.copyOf(optimisations), selector);

    return LimitDeterministicAutomatonBuilder.create(initialComponentBuilder,
      acceptingComponentBuilder,
      initialComponentBuilder::getJumps,
      getAnnotation,
      EnumSet.copyOf(optimisations),
      EquivalenceClass::isTrue);
  }

  private Iterable<EquivalenceClass> createInitialClasses(Factories factories, Formula formula) {
    EquivalenceClassStateFactory factory = new EquivalenceClassStateFactory(
      factories.equivalenceClassFactory, EnumSet.copyOf(optimisations));

    if (optimisations.contains(Optimisation.DETERMINISTIC_INITIAL_COMPONENT)) {
      return Collections.singleton(factory.getInitial(formula));
    }

    EquivalenceClass clazz = factories.equivalenceClassFactory.createEquivalenceClass(formula);
    return factory.splitEquivalenceClass(clazz);
  }
}
