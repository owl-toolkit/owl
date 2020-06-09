package owl.game.algorithms;

import static owl.game.Game.Owner.PLAYER_1;

import java.util.HashSet;
import java.util.Set;
import owl.automaton.acceptance.ParityAcceptance;
import owl.game.Game;

public interface ParityGameSolver {
  <S> boolean realizable(Game<S, ParityAcceptance> game);

  <S> WinningRegions<S> solve(Game<S, ParityAcceptance> game);

  final class WinningRegions<S> {
    final Set<S> player1;
    final Set<S> player2;

    WinningRegions(Set<S> s, Game.Owner o) {
      if (PLAYER_1 == o) {
        this.player1 = new HashSet<>(s);
        this.player2 = new HashSet<>();
      } else {
        this.player1 = new HashSet<>();
        this.player2 = new HashSet<>(s);
      }
    }

    void addAll(Set<S> s, Game.Owner o) {
      if (PLAYER_1 == o) {
        this.player1.addAll(s);
      } else {
        this.player2.addAll(s);
      }
    }

    Set<S> winningRegion(Game.Owner o) {
      return PLAYER_1 == o ? this.player1 : this.player2;
    }

    public Set<S> playerEven() {
      return this.player2;
    }

    public Set<S> playerOdd() {
      return this.player1;
    }

    public static <S> WinningRegions<S> of(Set<S> region, Game.Owner owner) {
      return new WinningRegions<>(region, owner);
    }
  }
}