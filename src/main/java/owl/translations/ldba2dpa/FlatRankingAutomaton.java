package owl.translations.ldba2dpa;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import owl.automaton.Automaton;
import owl.automaton.AutomatonFactory;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.ParityAcceptance.Parity;
import owl.automaton.edge.Edge;
import owl.automaton.ldba.LimitDeterministicAutomaton;

public final class FlatRankingAutomaton {
  private FlatRankingAutomaton() {}

  public static <S, T, A, L> Automaton<FlatRankingState<S, T>, ParityAcceptance> of(
    LimitDeterministicAutomaton<S, T, BuchiAcceptance, A> ldba, LanguageLattice<T, A, L> lattice,
    Predicate<S> isAcceptingState, boolean resetRanking, boolean optimizeInitialState) {
    checkArgument(//ldba.getAcceptingComponent().getInitialStates().isEmpty()
      ldba.initialComponent().initialStates().size() == 1,
      "Exactly one initial state expected.");

    Builder<S, T, A, L> builder = new Builder<>(ldba, lattice, isAcceptingState, resetRanking);

    // TODO: add getSensitiveAlphabet Method
    Automaton<FlatRankingState<S, T>, ParityAcceptance> automaton = AutomatonFactory.create(
      builder.initialState, builder.ldba.acceptingComponent().factory(),
      builder::getSuccessor, builder.acceptance);

    return optimizeInitialState ? AbstractBuilder.optimizeInitialState(automaton) : automaton;
  }

  static final class Builder<S, T, A, L> extends AbstractBuilder<S, T, A, L, BuchiAcceptance> {

    private static final Logger logger = Logger.getLogger(Builder.class.getName());

    private final boolean resetRanking;
    ParityAcceptance acceptance;
    final FlatRankingState<S, T> initialState;

    Builder(LimitDeterministicAutomaton<S, T, BuchiAcceptance, A> ldba,
      LanguageLattice<T, A, L> lattice, Predicate<S> isAcceptingState, boolean resetRanking) {
      super(ldba, lattice, isAcceptingState, resetRanking);
      logger.log(Level.FINER, "Safety Components: {0}", safetyComponents);
      acceptance = new ParityAcceptance(2 * Math.max(1, ldba.acceptingComponent().size() + 1),
        Parity.MIN_ODD);
      this.resetRanking = resetRanking;
      initialState = buildEdge(ldba.initialComponent().initialState(), List.of(), -1, null)
        .successor();
    }

    Edge<FlatRankingState<S, T>> buildEdge(S state, List<T> previousRanking,
      int previousSafetyProgress, @Nullable BitSet valuation) {
      if (isAcceptingState.test(state)) {
        return Edge.of(FlatRankingState.of(state), 1);
      }

      // We compute the relevant accepting components, which we can jump to.
      Map<A, Language<L>> existingLanguages = new HashMap<>();
      List<T> safetyTargets = new ArrayList<>();
      List<T> livenessTargets = new ArrayList<>();
      List<T> mixedTargets = new ArrayList<>();
      Language<L> emptyLanguage = lattice.getBottom();

      List<T> epsilonJumps = new ArrayList<>(ldba.epsilonJumps(state));
      epsilonJumps.sort(Comparator.comparingInt(x -> sortingOrder.indexOf(ldba.annotation(x))));

      for (T jumpTarget : epsilonJumps) {
        existingLanguages.put(ldba.annotation(jumpTarget), emptyLanguage);

        if (lattice.acceptsSafetyLanguage(jumpTarget)) {
          safetyTargets.add(jumpTarget);
        } else if (lattice.acceptsLivenessLanguage(jumpTarget)) {
          livenessTargets.add(jumpTarget);
        } else {
          mixedTargets.add(jumpTarget);
        }
      }

      // Default rejecting color.
      int edgeColor = 2 * previousRanking.size();
      List<T> ranking = new ArrayList<>(previousRanking.size());
      int safetyProgress = -1;

      boolean extendRanking = true;

      { // Compute componentMap successor
        ListIterator<T> iterator = previousRanking.listIterator();

        while (iterator.hasNext()) {
          assert valuation != null : "Valuation is only allowed to be null for empty rankings.";
          T previousState = iterator.next();
          Edge<T> edge = ldba.acceptingComponent().edge(previousState, valuation);

          if (edge == null) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          T rankingState = edge.successor();
          @Nullable
          A annotation = ldba.annotation(rankingState);
          Language<L> existingLanguage = existingLanguages.get(annotation);

          if (existingLanguage == null
            || existingLanguage.greaterOrEqual(lattice.getLanguage(rankingState))) {
            edgeColor = Math.min(2 * iterator.previousIndex(), edgeColor);
            continue;
          }

          existingLanguages.replace(annotation,
            existingLanguage.join(lattice.getLanguage(rankingState)));
          ranking.add(rankingState);

          if (lattice.acceptsSafetyLanguage(rankingState) && !iterator.hasNext()) {
            int safetyIndex = safetyComponents.indexOf(annotation);
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);

            logger.log(Level.FINER, "Found safety language {0} with safety index {1}.",
              new Object[] {rankingState, safetyIndex});

            if (resetRanking) {
              existingLanguages.replace(annotation, lattice.getTop());
            }

            if (previousSafetyProgress == safetyIndex) {
              safetyProgress = previousSafetyProgress;
              extendRanking = false;
              break;
            }
          } else if (edge.inSet(0)) {
            edgeColor = Math.min(2 * iterator.previousIndex() + 1, edgeColor);

            if (resetRanking) {
              existingLanguages.replace(annotation, lattice.getTop());
            }
          }
        }
      }

      logger.log(Level.FINER, "Ranking before extension: {0}.", ranking);

      // Extend the componentMap
      if (extendRanking) {
        for (T accState : livenessTargets) {
          if (insertableToRanking(accState, existingLanguages)) {
            ranking.add(accState);
          }
        }

        for (T accState : mixedTargets) {
          if (insertableToRanking(accState, existingLanguages)) {
            ranking.add(accState);
          }
        }

        T safety = findNextSafety(safetyTargets, previousSafetyProgress + 1);

        // Wrap around; restart search.
        if (safety == null) {
          safety = findNextSafety(safetyTargets, 0);
        }

        if (safety != null) {
          safetyProgress = safetyComponents.indexOf(ldba.annotation(safety));

          if (insertableToRanking(safety, existingLanguages)) {
            ranking.add(safety);
          }
        }
      }

      logger.log(Level.FINER, "Ranking after extension: {0}.", ranking);
      return Edge.of(FlatRankingState.of(state, ranking, safetyProgress), edgeColor);
    }

    @Nullable
    Edge<FlatRankingState<S, T>> getSuccessor(FlatRankingState<S, T> state, BitSet valuation) {
      S successor;

      { // We obtain the successor of the state in the initial component.
        Edge<S> edge = ldba.initialComponent().edge(state.state(), valuation);

        // The initial component moved to a rejecting sink. Thus all runs die.
        if (edge == null) {
          return null;
        }

        successor = edge.successor();
      }

      // If a SCC switch occurs, the componentMap and the safety progress is reset.
      if (sccSwitchOccurred(state.state(), successor)) {
        return Edge.of(buildEdge(successor, List.of(), -1, valuation).successor());
      }

      Edge<FlatRankingState<S, T>> edge = buildEdge(successor, state.ranking(),
        state.safetyProgress(), valuation);

      assert edge.largestAcceptanceSet() < acceptance.acceptanceSets();
      return edge;
    }
  }
}
