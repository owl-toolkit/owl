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

package owl.translations.ltl2dra;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.minimizations.MinimizationUtil;
import owl.automaton.minimizations.MinimizationUtil.MinimizationLevel;
import owl.ltl.BooleanConstant;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.Collector;
import owl.run.Environment;
import owl.translations.ldba2dra.MapRankingAutomaton;
import owl.translations.ltl2ldba.LTL2LDBAFunction;
import owl.translations.ltl2ldba.breakpointfree.BooleanLattice;
import owl.translations.ltl2ldba.breakpointfree.DegeneralizedBreakpointFreeState;
import owl.translations.ltl2ldba.breakpointfree.FGObligations;
import owl.translations.ltl2ldba.breakpointfree.GeneralizedBreakpointFreeState;

public class LTL2DRAFunction
  implements Function<LabelledFormula, Automaton<?, ? extends GeneralizedRabinAcceptance>> {

  private final EnumSet<Configuration> configuration;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    DegeneralizedBreakpointFreeState, BuchiAcceptance, FGObligations>> translatorBreakpointFree;
  private final Function<LabelledFormula, LimitDeterministicAutomaton<EquivalenceClass,
    GeneralizedBreakpointFreeState, GeneralizedBuchiAcceptance, FGObligations>>
    translatorGeneralizedBreakpointFree;

  public LTL2DRAFunction(Environment env, Set<Configuration> configuration) {
    this.configuration = EnumSet.copyOf(configuration);

    EnumSet<LTL2LDBAFunction.Configuration> ldbaConfiguration = EnumSet.of(
      LTL2LDBAFunction.Configuration.EAGER_UNFOLD,
      LTL2LDBAFunction.Configuration.EPSILON_TRANSITIONS,
      LTL2LDBAFunction.Configuration.SUPPRESS_JUMPS);
    // TODO: There is somewhere a heisenbug with that flag SUPPRESS_JUMPS.

    if (configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE)) {
      ldbaConfiguration.add(LTL2LDBAFunction.Configuration.OPTIMISED_STATE_STRUCTURE);
    }

    translatorBreakpointFree =
      LTL2LDBAFunction.createDegeneralizedBreakpointFreeLDBABuilder(env, ldbaConfiguration);
    translatorGeneralizedBreakpointFree =
      LTL2LDBAFunction.createGeneralizedBreakpointFreeLDBABuilder(env, ldbaConfiguration);
  }

  @Override
  public Automaton<?, ? extends GeneralizedRabinAcceptance> apply(LabelledFormula formula) {
    Automaton<?, ? extends GeneralizedRabinAcceptance> automaton =
      configuration.contains(Configuration.DEGENERALIZE)
      ? applyDegeneralized(formula)
      : applyGeneralized(formula);

    return MinimizationUtil.minimizeDefault(AutomatonUtil.asMutable(automaton),
      MinimizationLevel.ALL);
  }

  private Automaton<?, RabinAcceptance> applyDegeneralized(LabelledFormula formula) {
    LimitDeterministicAutomaton<EquivalenceClass, DegeneralizedBreakpointFreeState,
      BuchiAcceptance, FGObligations> ldba = translatorBreakpointFree.apply(formula);

    if (ldba.isDeterministic()) {
      return Views.viewAs(ldba.getAcceptingComponent(), RabinAcceptance.class);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    return MapRankingAutomaton.of((LimitDeterministicAutomaton) ldba, new BooleanLattice(),
      this::hasSafetyCore, true,
      configuration.contains(Configuration.OPTIMISE_INITIAL_STATE));
  }

  private Automaton<?, GeneralizedRabinAcceptance> applyGeneralized(LabelledFormula formula) {
    LimitDeterministicAutomaton<EquivalenceClass, GeneralizedBreakpointFreeState,
      GeneralizedBuchiAcceptance, FGObligations> ldba = translatorGeneralizedBreakpointFree
      .apply(formula);

    if (ldba.isDeterministic()) {
      return Views.viewAs(ldba.getAcceptingComponent(), GeneralizedRabinAcceptance.class);
    }

    assert ldba.getInitialComponent().getInitialStates().size() == 1;
    assert ldba.getAcceptingComponent().getInitialStates().isEmpty();

    return MapRankingAutomaton.of((LimitDeterministicAutomaton) ldba, new BooleanLattice(),
      this::hasSafetyCore, true,
      configuration.contains(Configuration.OPTIMISE_INITIAL_STATE));
  }

  private boolean hasSafetyCore(EquivalenceClass state) {
    if (state.testSupport(Fragments::isSafety)) {
      return true;
    }

    // Check if the state has an independent safety core.
    if (configuration.contains(Configuration.EXISTS_SAFETY_CORE)) {
      BitSet nonSafety = Collector.collectAtoms(state.getSupport(x -> !Fragments.isSafety(x)));

      EquivalenceClass core = state.substitute(x -> {
        if (!Fragments.isSafety(x)) {
          return BooleanConstant.FALSE;
        }

        BitSet ap = Collector.collectAtoms(x);
        assert !ap.isEmpty() : "Formula " + x + " has empty AP.";
        return ap.intersects(nonSafety) ? x : BooleanConstant.TRUE;
      });

      return core.isTrue();
    }

    return false;
  }

  public enum Configuration {
    OPTIMISE_INITIAL_STATE, OPTIMISED_STATE_STRUCTURE, EXISTS_SAFETY_CORE, DEGENERALIZE
  }
}
