package owl.translations.ldba2dpa;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.algorithms.SccDecomposition;
import owl.automaton.ldba.LimitDeterministicAutomaton;
import owl.automaton.util.AnnotatedState;

public class AbstractBuilder<S, T, A, L, B extends GeneralizedBuchiAcceptance> {

  @Nullable
  private final List<Set<S>> initialComponentSccs;
  protected final Predicate<S> isAcceptingState;
  protected final LanguageLattice<T, A, L> lattice;
  protected final LimitDeterministicAutomaton<S, T, B, A> ldba;
  protected final List<A> safetyComponents;
  protected final List<A> sortingOrder;

  protected AbstractBuilder(LimitDeterministicAutomaton<S, T, B, A> ldba,
    LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, boolean resetAfterSccSwitch) {
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

  public static <S, A extends OmegaAcceptance, S2 extends AnnotatedState<S>>
    Automaton<S2, A> optimizeInitialState(Automaton<S2, A> readOnly) {
    S originalInitialState = readOnly.initialState().state();

    if (originalInitialState == null) {
      return readOnly;
    }

    MutableAutomaton<S2, A> automaton = AutomatonUtil.asMutable(readOnly);

    S2 potentialInitialState = automaton.initialState();
    int size = automaton.size();

    for (Set<S2> scc : SccDecomposition.computeSccs(automaton, false)) {
      for (S2 state : scc) {
        if (!originalInitialState.equals(state.state())) {
          continue;
        }

        int newSize = AutomatonUtil.getReachableStates(automaton, Set.of(state)).size();

        if (newSize < size) {
          size = newSize;
          potentialInitialState = state;
        }
      }
    }

    automaton.initialState(potentialInitialState);
    automaton.removeUnreachableStates();
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
