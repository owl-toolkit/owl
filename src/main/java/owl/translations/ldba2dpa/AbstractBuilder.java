package owl.translations.ldba2dpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.MutableAutomaton;
import owl.automaton.MutableAutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.util.AnnotatedState;

public class AbstractBuilder<S, T, A, L, B extends GeneralizedBuchiAcceptance> {

  @Nullable
  private final List<Set<S>> initialComponentSccs;
  protected final Predicate<? super S> isAcceptingState;
  protected final LanguageLattice<T, A, L> lattice;
  protected final LimitDeterministicAutomaton<S, T, B, A> ldba;
  protected final List<A> safetyComponents;
  protected final List<A> sortingOrder;

  protected AbstractBuilder(LimitDeterministicAutomaton<S, T, B, A> ldba,
    LanguageLattice<T, A, L> lattice, Predicate<? super S> isAcceptingState,
    boolean resetAfterSccSwitch) {
    initialComponentSccs = resetAfterSccSwitch
      ? SccDecomposition.computeSccs(ldba.initialComponent())
      : null;
    this.lattice = lattice;
    this.ldba = ldba;
    sortingOrder = List.copyOf(ldba.components());

    // Identify  safety components.
    List<A> safetyBuilder = new ArrayList<>();

    for (A value : sortingOrder) {
      if (lattice.isSafetyAnnotation(value)) {
        safetyBuilder.add(value);
      }
    }

    safetyComponents = List.copyOf(safetyBuilder);
    this.isAcceptingState = isAcceptingState;
  }

  public static <S extends AnnotatedState<?>, A extends OmegaAcceptance> Automaton<S, A>
    optimizeInitialState(Automaton<S, A> readOnly) {
    var originalInitialState = readOnly.onlyInitialState().state();
    MutableAutomaton<S, A> automaton = MutableAutomatonUtil.asMutable(readOnly);
    int size = automaton.size();

    for (Set<S> scc : SccDecomposition.computeSccs(automaton, false)) {
      for (S state : scc) {
        if (!originalInitialState.equals(state.state()) || !automaton.states().contains(state)) {
          continue;
        }

        int newSize = Views.replaceInitialState(automaton, Set.of(state)).size();

        if (newSize < size) {
          size = newSize;
          automaton.initialStates(Set.of(state));
          automaton.trim();
        }
      }
    }

    return automaton;
  }

  @Nullable
  protected T findNextSafety(List<T> availableJumps, int i) {
    for (A annotation : safetyComponents.subList(i, safetyComponents.size())) {
      for (T state : availableJumps) {
        assert lattice.acceptsSafetyLanguage(state);

        A stateAnnotation = ldba.annotation(state);

        if (annotation.equals(stateAnnotation)) {
          return state;
        }
      }
    }

    return null;
  }

  protected boolean insertableToRanking(T state, Map<A, Language<L>> existingLanguages) {
    Language<L> existingClass = existingLanguages.get(ldba.annotation(state));
    Language<L> stateClass = lattice.getLanguage(state);
    return existingClass == null || !existingClass.greaterOrEqual(stateClass);
  }

  protected boolean sccSwitchOccurred(S state, S successor) {
    return initialComponentSccs != null && initialComponentSccs.stream()
      .anyMatch(x -> x.contains(state) && !x.contains(successor));
  }
}
