package owl.translations.dra2dpa;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import owl.automaton.Automaton;
import owl.automaton.AutomatonUtil;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;

public final class IARFactory {
  private static final Logger logger = Logger.getLogger(IARFactory.class.getName());

  public static <R> Function<Automaton<R, RabinAcceptance>,
    Automaton<IARState<R>, ParityAcceptance>> translator() {
    return rabinAutomaton -> {
      logger.log(Level.FINEST, () -> String.format("Applying IAR to input automaton:%n%s",
        AutomatonUtil.toHoa(rabinAutomaton)));
      try {
        return new IARBuilder<>(rabinAutomaton).build();
      } catch (ExecutionException e) {
        //noinspection ProhibitedExceptionThrown
        throw new RuntimeException(e); // NOPMD
      }
    };
  }

  private IARFactory() {}
}
