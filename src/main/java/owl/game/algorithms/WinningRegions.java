package owl.game.algorithms;

import static owl.game.Game.Owner.ENVIRONMENT;

import java.util.HashSet;
import java.util.Set;
import owl.game.Game;

final class WinningRegions<S> {
  final Set<S> player1;
  final Set<S> player2;

  WinningRegions(Set<S> s, Game.Owner o) {
    if (ENVIRONMENT == o) {
      this.player1 = new HashSet<>(s);
      this.player2 = new HashSet<>();
    } else {
      this.player1 = new HashSet<>();
      this.player2 = new HashSet<>(s);
    }
  }

  void addAll(Set<S> s, Game.Owner o) {
    if (ENVIRONMENT == o) {
      this.player1.addAll(s);
    } else {
      this.player2.addAll(s);
    }
  }

  Set<S> winningRegion(Game.Owner o) {
    return ENVIRONMENT == o ? this.player1 : this.player2;
  }
}
