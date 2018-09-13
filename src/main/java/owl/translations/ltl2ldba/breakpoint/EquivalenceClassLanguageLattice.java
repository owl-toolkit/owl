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

package owl.translations.ltl2ldba.breakpoint;

import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.SyntacticFragment;
import owl.translations.ldba2dpa.Language;
import owl.translations.ldba2dpa.LanguageLattice;

public class EquivalenceClassLanguageLattice implements
  LanguageLattice<DegeneralizedBreakpointState, GObligations, EquivalenceClass> {

  private final EquivalenceClassFactory eqFactory;

  public EquivalenceClassLanguageLattice(EquivalenceClassFactory eqFactory) {
    this.eqFactory = eqFactory;
  }

  @Override
  public Language<EquivalenceClass> getBottom() {
    return new EquivalenceClassLanguage(eqFactory.getFalse());
  }

  @Override
  public boolean acceptsLivenessLanguage(DegeneralizedBreakpointState state) {
    return isLivenessLanguage(state.obligations) && state.next.length == 0 && state.safety.isTrue()
      && state.current.modalOperators().stream().allMatch(SyntacticFragment.CO_SAFETY::contains);
  }

  @Override
  public Language<EquivalenceClass> getTop() {
    return new EquivalenceClassLanguage(eqFactory.getTrue());
  }

  @Override
  public boolean acceptsSafetyLanguage(DegeneralizedBreakpointState state) {
    return isSafetyAnnotation(state.obligations) && state.next.length == 0
      && state.current.modalOperators().stream().allMatch(SyntacticFragment.SAFETY::contains);
  }

  @Override
  public Language<EquivalenceClass> getLanguage(DegeneralizedBreakpointState state) {
    return new EquivalenceClassLanguage(state.getLabel());
  }

  @Override
  public boolean isLivenessLanguage(GObligations annotation) {
    return annotation.obligations.isEmpty()
      && annotation.safetyAutomaton.onlyInitialState().isTrue();
  }

  @Override
  public boolean isSafetyAnnotation(GObligations annotation) {
    return annotation.obligations.isEmpty() && annotation.liveness.isEmpty();
  }

  private static class EquivalenceClassLanguage implements Language<EquivalenceClass> {
    private final EquivalenceClass eq;

    EquivalenceClassLanguage(EquivalenceClass eq) {
      this.eq = eq;
    }

    @Override
    public EquivalenceClass getT() {
      return eq;
    }

    @Override
    public boolean greaterOrEqual(Language<EquivalenceClass> language) {
      return language.getT().implies(eq);
    }

    @Override
    public boolean isBottom() {
      return eq.isFalse();
    }

    @Override
    public boolean isTop() {
      return eq.isTrue();
    }

    @Override
    public Language<EquivalenceClass> join(Language<EquivalenceClass> language) {
      return new EquivalenceClassLanguage(eq.or(language.getT()));
    }
  }
}
