package owl.game.algorithms;

import com.google.common.collect.Collections2;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import owl.automaton.Automaton;
import owl.automaton.AutomatonOperations;
import owl.automaton.AutomatonUtil;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.CoBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.transformations.BuchiDegeneralization;
import owl.game.Game;
import owl.game.GameFactory;
import owl.game.GameViews;
import owl.translations.dpa2safety.DPA2Safety;

public class CompositionalSolver {

  public static boolean isRealizable(
    List<Automaton<Object, ParityAcceptance>> parityAutomata,
    List<Automaton<Object, BuchiAcceptance>> buchiAutomata,
    List<Automaton<Object, CoBuchiAcceptance>> coBuchiAutomata,
    List<Automaton<Object, AllAcceptance>> safety,
    int threshold,
    List<String> firstPropositions) {

    DPA2Safety dpa2safety = new DPA2Safety();

    boolean allWin = false;

    var winning = new boolean[parityAutomata.size()];
    var thresholds = new int[parityAutomata.size()];

    while (Arrays.stream(thresholds).allMatch(x -> x <= threshold) && !allWin) {
      allWin = true;

      for (int i = 0; i < parityAutomata.size(); i++) {
        if (winning[i]) {
          continue;
        }

        Automaton<Object, AllAcceptance> safetyAutomaton = AutomatonUtil
          .cast(dpa2safety.apply(parityAutomata.get(i), thresholds[i]), AllAcceptance.class);
        Game<GameViews.Node<Object>, AllAcceptance> safetyGame = GameFactory
          .copyOf(GameViews.split(safetyAutomaton, firstPropositions));
        Set<GameViews.Node<Object>> unsafeStates = Sets
          .filter(safetyGame.states(), x -> safetyGame.successors(x).isEmpty());
        var unsafeStatesAttractor = AttractorSolver
          .getAttractor(safetyGame, unsafeStates, Game.Owner.PLAYER_1);

        if (!unsafeStatesAttractor.contains(safetyGame.onlyInitialState())) {
          winning[i] = true;
        } else {
          allWin = false;
          thresholds[i] = thresholds[i] + 1;
        }
      }
    }

    if (Arrays.stream(thresholds).anyMatch(x -> x > threshold)) {
      return false;
    }

    var buchiAutomaton = buchiAutomata.isEmpty()
      ? null
      : BuchiDegeneralization.degeneralize(
        AutomatonUtil
          .cast(AutomatonOperations.intersection(buchiAutomata), GeneralizedBuchiAcceptance.class));

    for (int k = 0; k <= threshold; k++) {
      List<Automaton<Object, AllAcceptance>> safetyAutomatonList = new ArrayList<>(safety);

      for (int i = 0; i < parityAutomata.size(); i++) {
        var safetyAutomaton = dpa2safety.apply(parityAutomata.get(i), thresholds[i] + k);
        safetyAutomatonList
          .add(AutomatonUtil.cast(safetyAutomaton, Object.class, AllAcceptance.class));
      }

      var safetyProductAutomaton = safetyAutomatonList.isEmpty()
        ? null
        : AutomatonOperations.intersection(safetyAutomatonList);

      var safetyGame = GameFactory
        .copyOf(GameViews.split(safetyProductAutomaton, firstPropositions));
      var unsafeStates = Sets
        .filter(safetyGame.states(), x -> safetyGame.successors(x).isEmpty());
      var unsafeStatesAttractor = Set.copyOf(Collections2.transform(
        AttractorSolver.getAttractor(safetyGame, unsafeStates, Game.Owner.PLAYER_1),
        GameViews.Node::state));

      safetyProductAutomaton = Views.filter(safetyProductAutomaton,
        Sets.filter(safetyProductAutomaton.states(), x -> !unsafeStatesAttractor.contains(x)));

      if (!safetyProductAutomaton.initialStates().isEmpty()) {
        List<Automaton<Object, OmegaAcceptance>> builder = new ArrayList<>();
        builder
          .add(AutomatonUtil.cast(safetyProductAutomaton, Object.class, OmegaAcceptance.class));

        if (buchiAutomaton != null) {
          builder.add(AutomatonUtil.cast(buchiAutomaton, Object.class, OmegaAcceptance.class));
        }

        for (var aut : coBuchiAutomata) {
          builder.add(AutomatonUtil.cast(aut, Object.class, OmegaAcceptance.class));
        }

        Automaton<?, OmegaAcceptance> product = AutomatonOperations.intersection(builder);
        WinningRegions<GameViews.Node<Object>> winningRegions;
        GameViews.Node<Object> initialState;

        if (product.acceptance() instanceof RabinAcceptance) {
          var game = GameViews
            .split(AutomatonUtil.cast(product, Object.class, RabinAcceptance.class),
              firstPropositions);
          winningRegions = ZielonkaSolver.solveRabinPair(game);
          initialState = game.onlyInitialState();
        } else if (product.acceptance() instanceof AllAcceptance) {
          var game = GameViews.split(AutomatonUtil.cast(product, Object.class, AllAcceptance.class),
            firstPropositions);
          winningRegions = AttractorSolver.solveSafety(game);
          initialState = game.onlyInitialState();
        } else {
          var game = GameViews
            .split(AutomatonUtil.cast(product, Object.class, BuchiAcceptance.class),
              firstPropositions);
          winningRegions = ZielonkaSolver.solveBuchi(game);
          initialState = game.onlyInitialState();
        }

        if (winningRegions.player2.contains(initialState)) {
          return true;
        }
      }
    }

    return false;
  }

}
