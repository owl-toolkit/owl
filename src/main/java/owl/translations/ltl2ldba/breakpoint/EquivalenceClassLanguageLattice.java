package owl.translations.ltl2ldba.breakpoint;

import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.translations.ldba2dpa.Language;
import owl.translations.ldba2dpa.LanguageLattice;

public class EquivalenceClassLanguageLattice implements LanguageLattice<EquivalenceClass,
  DegeneralizedBreakpointState, GObligations> {

  private final EquivalenceClassFactory eqFactory;

  public EquivalenceClassLanguageLattice(EquivalenceClassFactory eqFactory) {
    this.eqFactory = eqFactory;
  }

  @Override
  public Language<EquivalenceClass> getLanguage(DegeneralizedBreakpointState state,
      boolean current) {
    if (current) {
      return new EquivalenceClassLanguage(state.getCurrent());
    } else {
      return new EquivalenceClassLanguage(state.getLabel());
    }
  }

  @Override
  public Language<EquivalenceClass> getBottom() {
    return new EquivalenceClassLanguage(eqFactory.getFalse());
  }

  @Override
  public Language<EquivalenceClass> getTop() {
    return new EquivalenceClassLanguage(eqFactory.getTrue());
  }

  @Override
  public boolean isLivenessLanguage(GObligations annotation) {
    return annotation.isPureLiveness();
  }

  @Override
  public boolean isSafetyLanguage(GObligations annotation) {
    return annotation.isPureSafety();
  }

  private static class EquivalenceClassLanguage implements Language<EquivalenceClass> {
    private final EquivalenceClass eq;

    EquivalenceClassLanguage(EquivalenceClass eq) {
      this.eq = eq;
    }

    @Override
    public Language<EquivalenceClass> join(Language<EquivalenceClass> language) {
      return new EquivalenceClassLanguage(eq.orWith(language.getT()));
    }

    @Override
    public boolean greaterOrEqual(Language<EquivalenceClass> language) {
      return language.getT().implies(eq);
    }

    @Override
    public boolean isTop() {
      return eq.isTrue();
    }

    @Override
    public boolean isBottom() {
      return eq.isFalse();
    }

    @Override
    public void free() {
      eq.free();
    }

    @Override
    public EquivalenceClass getT() {
      return eq;
    }
  }
}
