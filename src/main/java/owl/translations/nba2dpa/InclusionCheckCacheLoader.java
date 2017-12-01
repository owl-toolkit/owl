package owl.translations.nba2dpa;

import com.google.common.cache.CacheLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.algorithms.EmptinessCheck;

public class InclusionCheckCacheLoader<S> extends CacheLoader<Entry<Set<S>, S>, Boolean> {

  private final Automaton<S, ?> automaton;

  InclusionCheckCacheLoader(Automaton<S, ?> automaton) {
    this.automaton = automaton;
  }

  @Override
  public Boolean load(Entry<Set<S>, S> entry) {
    if (entry.getKey().contains(entry.getValue())) {
      return true;
    }

    List<S> initialState = new ArrayList<>(entry.getKey());
    initialState.add(entry.getValue());
    RabinAcceptance acceptance = new RabinAcceptance(1);
    Automaton<List<S>, RabinAcceptance> prodAutomaton = Views.createProductAutomaton(
      acceptance, initialState, automaton);
    return !EmptinessCheck.isEmpty(prodAutomaton);
  }
}
