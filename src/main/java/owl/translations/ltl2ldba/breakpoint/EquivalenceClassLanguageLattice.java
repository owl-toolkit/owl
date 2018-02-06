package owl.translations.ltl2ldba.breakpoint;

import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
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
      && state.current.testSupport(Fragments::isCoSafety);
  }

  @Override
  public Language<EquivalenceClass> getTop() {
    return new EquivalenceClassLanguage(eqFactory.getTrue());
  }

  @Override
  public boolean acceptsSafetyLanguage(DegeneralizedBreakpointState state) {
    return isSafetyAnnotation(state.obligations) && state.next.length == 0 && state.current
      .testSupport(Fragments::isSafety);
  }

  @Override
  public Language<EquivalenceClass> getLanguage(DegeneralizedBreakpointState state) {
    return new EquivalenceClassLanguage(state.getLabel());
  }

  @Override
  public boolean isLivenessLanguage(GObligations annotation) {
    return annotation.obligations.length == 0 && annotation.safety.isTrue();
  }

  @Override
  public boolean isSafetyAnnotation(GObligations annotation) {
    return annotation.obligations.length == 0 && annotation.liveness.length == 0;
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
