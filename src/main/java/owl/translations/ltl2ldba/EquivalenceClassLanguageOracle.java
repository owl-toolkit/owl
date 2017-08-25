package owl.translations.ltl2ldba;

import owl.factories.EquivalenceClassFactory;
import owl.ltl.EquivalenceClass;
import owl.ltl.Fragments;
import owl.translations.ltl2dpa.Language;
import owl.translations.ltl2dpa.LanguageOracle;
import owl.translations.ltl2ldba.breakpoint.DegeneralizedBreakpointState;
import owl.translations.ltl2ldba.breakpoint.GObligations;

public class EquivalenceClassLanguageOracle
  implements LanguageOracle<EquivalenceClass, DegeneralizedBreakpointState, GObligations> {

  private final EquivalenceClassFactory eqFactory;

  public EquivalenceClassLanguageOracle(EquivalenceClassFactory eqFactory) {
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
  public Language<EquivalenceClass> getEmpty() {
    return new EquivalenceClassLanguage(eqFactory.getFalse());
  }

  @Override
  public Language<EquivalenceClass> getUniverse() {
    return new EquivalenceClassLanguage(eqFactory.getTrue());
  }

  @Override
  public boolean isPureLiveness(GObligations obligations) {
    return obligations.isPureLiveness();
  }

  @Override
  public boolean isPureSafety(GObligations obligations) {
    return obligations.isPureSafety();
  }

  public static class EquivalenceClassLanguage implements Language<EquivalenceClass> {

    private final EquivalenceClass eq;

    EquivalenceClassLanguage(EquivalenceClass eq) {
      this.eq = eq;
    }

    @Override
    public Language<EquivalenceClass> union(Language<EquivalenceClass> l) {
      return new EquivalenceClassLanguage(eq.orWith(l.getLanguageObject()));
    }

    @Override
    public boolean contains(Language<EquivalenceClass> l) {
      return l.getLanguageObject().implies(eq);
    }

    @Override
    public boolean isUniverse() {
      return eq.isTrue();
    }

    @Override
    public boolean isEmpty() {
      return eq.isFalse();
    }

    @Override
    public void free() {
      eq.free();
    }

    @Override
    public boolean isSafetyLanguage() {
      return eq.testSupport(Fragments::isSafety);
    }

    @Override
    public boolean isCosafetyLanguage() {
      return eq.testSupport(Fragments::isCoSafety);
    }

    @Override
    public EquivalenceClass getLanguageObject() {
      return eq;
    }
  }
}
