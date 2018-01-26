package owl.translations.nba2dpa;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import owl.automaton.Automaton;
import owl.automaton.Automaton.Property;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.algorithms.LanguageAnalysis;
import owl.translations.ldba2dpa.Language;
import owl.translations.ldba2dpa.LanguageLattice;

class SetLanguageLattice<S> implements LanguageLattice<S, Void, Set<S>> {
  private final Language<Set<S>> bottom;
  private final LoadingCache<Entry<Set<S>, Set<S>>, Boolean> greaterOrEqualCache;
  private final Language<Set<S>> top;

  SetLanguageLattice(Automaton<S, BuchiAcceptance> automaton) {
    assert automaton.is(Property.COMPLETE) : "Only complete automata supported.";
    assert automaton.is(Property.DETERMINISTIC) : "Only deterministic automata supported.";

    bottom = new SetLanguage(Set.of());
    top = new SetLanguage(ImmutableSet.copyOf(automaton.getStates()));
    greaterOrEqualCache = CacheBuilder.newBuilder().maximumSize(500000)
      .expireAfterAccess(60, TimeUnit.SECONDS).build(new Loader<>(automaton));
  }

  @Override
  public boolean acceptsLivenessLanguage(S state) {
    return false;
  }

  @Override
  public boolean acceptsSafetyLanguage(S state) {
    return false;
  }

  @Override
  public Language<Set<S>> getBottom() {
    return bottom;
  }

  @Override
  public Language<Set<S>> getLanguage(S state) {
    return new SetLanguage(Set.of(state));
  }

  @Override
  public Language<Set<S>> getTop() {
    return top;
  }

  @Override
  public boolean isLivenessLanguage(Void annotation) {
    return false;
  }

  @Override
  public boolean isSafetyAnnotation(Void annotation) {
    return false;
  }

  private static class Loader<S>
    extends CacheLoader<Entry<Set<S>, Set<S>>, Boolean> {

    private final Automaton<S, BuchiAcceptance> automaton;

    Loader(Automaton<S, BuchiAcceptance> automaton) {
      this.automaton = automaton;
    }

    @Override
    public Boolean load(Entry<Set<S>, Set<S>> entry) {
      return LanguageAnalysis.contains(union(entry.getValue()), union(entry.getKey()));
    }

    private Automaton<List<S>, BuchiAcceptance> union(Set<S> initialStates) {
      List<Automaton<S, BuchiAcceptance>> automata = new ArrayList<>();

      for (S initialState : initialStates) {
        automata.add(Views.replaceInitialState(automaton, Set.of(initialState)));
      }

      return AutomatonUtil.cast(AutomatonOperations.union(automata), BuchiAcceptance.class);
    }
  }

  private class SetLanguage implements Language<Set<S>> {

    private final Set<S> set;

    private SetLanguage(Set<S> set) {
      this.set = set;
    }

    @Override
    public Set<S> getT() {
      return set;
    }

    @Override
    public boolean greaterOrEqual(Language<Set<S>> language) {
      Set<S> otherSet = language.getT();

      if (set.containsAll(language.getT())) {
        return true;
      }

      if (set.isEmpty()) {
        return false;
      }

      return greaterOrEqualCache.getUnchecked(new AbstractMap.SimpleEntry<>(set, otherSet));
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
