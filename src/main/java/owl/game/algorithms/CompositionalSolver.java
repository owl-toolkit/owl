package owl.game.algorithms;

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
        Game<Object, AllAcceptance> safetyGame = Game.of(safetyAutomaton, firstPropositions);
        Set<Object> unsafeStates = Sets
          .filter(safetyGame.automaton().states(), x -> safetyGame.automaton().successors(x).isEmpty());
        var unsafeStatesAttractor = AttractorSolver
          .compute(safetyGame.automaton(), unsafeStates, true, safetyGame.variables(Game.Owner.ENVIRONMENT));

        if (!unsafeStatesAttractor.contains(safetyGame.automaton().onlyInitialState())) {
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

      var safetyGame = Game.of(safetyProductAutomaton, firstPropositions);
      var unsafeStates = Sets.filter(safetyGame.automaton().states(), x -> safetyGame.automaton().successors(x).isEmpty());
      var unsafeStatesAttractor = AttractorSolver.compute(safetyGame.automaton(), unsafeStates, true, safetyGame.variables(Game.Owner.ENVIRONMENT)));

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
        WinningRegions<Object> winningRegions;
        Object initialState;

        if (product.acceptance() instanceof RabinAcceptance) {
          var game = Game.of(AutomatonUtil.cast(product, Object.class, RabinAcceptance.class),
              firstPropositions);
          winningRegions = ZielonkaSolver.solveRabinPair(game);
          initialState = game.automaton().onlyInitialState();
        } else if (product.acceptance() instanceof AllAcceptance) {
          var game = Game.of(AutomatonUtil.cast(product, Object.class, AllAcceptance.class),
            firstPropositions);
          winningRegions = AttractorSolver.solveSafety(game);
          initialState = game.automaton().onlyInitialState();
        } else {
          var game = Game.of(AutomatonUtil.cast(product, Object.class, BuchiAcceptance.class),
              firstPropositions);
          winningRegions = ZielonkaSolver.solveBuchi(game);
          initialState = game.automaton().onlyInitialState();
        }

        if (winningRegions.player2.contains(initialState)) {
          return true;
        }
      }
    }

    return false;
  }

}
