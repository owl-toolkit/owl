package owl.translations.nba2dpa;

import com.google.common.cache.CacheLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import owl.automaton.Automaton;
import owl.automaton.ProductAutomaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.minimizations.MinimizationUtil;

public class InclusionCheckCacheLoader<S, T> extends CacheLoader<Entry<Set<S>,
S>, Boolean> {
  
  private final LimitDeterministicAutomaton<T, S, BuchiAcceptance, Void>
  ldba;

  public InclusionCheckCacheLoader(
      LimitDeterministicAutomaton<T, S, BuchiAcceptance, Void> ldba) {
    this.ldba = ldba;
  }

  public Boolean load(Entry<Set<S>, S> sets) {
    if (sets.getKey().contains(sets.getValue())) {
      return true;
    }
    List<S> initialState = new ArrayList<>(sets.getKey());
    initialState.add(sets.getValue());
    RabinAcceptance acceptance = new RabinAcceptance(1);
    Automaton<List<S>, RabinAcceptance> prodAutomaton = ProductAutomaton.createProductAutomaton(
        acceptance, initialState, ldba);
    return MinimizationUtil.hasAcceptingRun(prodAutomaton, initialState);
  }
}
