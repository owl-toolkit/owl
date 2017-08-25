package owl.translations.nba2dpa;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import owl.translations.ltl2dpa.Language;
import owl.translations.ltl2dpa.LanguageOracle;
import owl.translations.nba2ldba.BreakpointState;

public class StateSetLanguageOracle<S>
  implements LanguageOracle<Set<BreakpointState<S>>, BreakpointState<S>, Void> {

  private final ImmutableSet<BreakpointState<S>> universe;

  StateSetLanguageOracle(Set<BreakpointState<S>> universe) {
    this.universe = ImmutableSet.copyOf(universe);
  }

  @Override
  public Language<Set<BreakpointState<S>>> getLanguage(BreakpointState<S> state, boolean current) {
    return new SLanguage(ImmutableSet.of(state));
  }

  @Override
  public Language<Set<BreakpointState<S>>> getEmpty() {
    return new SLanguage(ImmutableSet.of());
  }

  @Override
  public Language<Set<BreakpointState<S>>> getUniverse() {
    return new SLanguage(universe);
  }

  @Override
  public boolean isPureLiveness(Void obligations) {
    return false;
  }

  @Override
  public boolean isPureSafety(Void obligations) {
    return false;
  }

  private class SLanguage implements Language<Set<BreakpointState<S>>> {

    private final ImmutableSet<BreakpointState<S>> set;

    SLanguage(Set<BreakpointState<S>> set) {
      this.set = ImmutableSet.copyOf(set);
    }

    @Override
    public Language<Set<BreakpointState<S>>> union(Language<Set<BreakpointState<S>>> l) {
      return new SLanguage(Sets.union(set, l.getLanguageObject()));
    }

    @Override
    public boolean contains(Language<Set<BreakpointState<S>>> l) {
      return set.containsAll(l.getLanguageObject());
    }

    @Override
    public boolean isUniverse() {
      return set.equals(universe);
    }

    @Override
    public boolean isEmpty() {
      return set.isEmpty();
    }

    @Override
    public void free() {
      //Does nothing
    }

    @Override
    public boolean isSafetyLanguage() {
      return false;
    }

    @Override
    public boolean isCosafetyLanguage() {
      return false;
    }

    @Override
    public Set<BreakpointState<S>> getLanguageObject() {
      return set;
    }
  }
}