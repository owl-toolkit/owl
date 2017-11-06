package owl.translations.nba2dpa;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.AbstractMap;
import java.util.Map.Entry;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.translations.ldba2dpa.Language;
import owl.translations.ldba2dpa.LanguageLattice;
import owl.translations.nba2ldba.Safety;

public class SetLanguageLattice<S> implements LanguageLattice<Set<S>, S, Safety> {

  private final Language<Set<S>> bottom;
  private final Language<Set<S>> top;
  private final LoadingCache<Entry<Set<S>, S>, Boolean> cache;

  SetLanguageLattice(Automaton<S, BuchiAcceptance> automaton) {
    bottom = new SetLanguage(ImmutableSet.of());
    top = new SetLanguage(ImmutableSet.copyOf(automaton.getStates()));
    cache = CacheBuilder.newBuilder().maximumSize(30000)
      .build(new InclusionCheckCacheLoader<>(automaton));
  }

  @Override
  public Language<Set<S>> getBottom() {
    return bottom;
  }

  @Override
  public Language<Set<S>> getLanguage(S state, boolean current) {
    return new SetLanguage(ImmutableSet.of(state));
  }

  @Override
  public Language<Set<S>> getTop() {
    return top;
  }

  @Override
  public boolean isLivenessLanguage(Safety annotation) {
    return Safety.CO_SAFETY == annotation;
  }

  @Override
  public boolean isSafetyLanguage(Safety annotation) {
    return Safety.SAFETY == annotation;
  }

  private class SetLanguage implements Language<Set<S>> {

    private final ImmutableSet<S> set;

    SetLanguage(Set<S> set) {
      this.set = ImmutableSet.copyOf(set);
    }

    @Override
    public Set<S> getT() {
      return set;
    }

    @Override
    public boolean greaterOrEqual(Language<Set<S>> language) {
      if (set.containsAll(language.getT())) {
        return true;
      }

      for (S q : language.getT()) {
        if (cache.getUnchecked(new AbstractMap.SimpleEntry<>(set, q))) {
          return false;
        }
      }

      return true;
    }

    @Override
    public boolean isBottom() {
      return set.isEmpty();
    }

    @Override
    public boolean isTop() {
      return set.equals(top.getT());
    }

    @Override
    public Language<Set<S>> join(Language<Set<S>> language) {
      return new SetLanguage(Sets.union(set, language.getT()).immutableCopy());
    }
  }

}
