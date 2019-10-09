package owl.translations.canonical;

import java.util.Optional;
import java.util.function.Function;
import owl.automaton.Automaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.ltl.LabelledFormula;
import owl.run.Environment;

abstract class AbstractPortfolio<A extends OmegaAcceptance>
  implements Function<LabelledFormula, Optional<Automaton<?, A>>> {

  final Class<A> acceptanceBound;
  final Environment environment;

  AbstractPortfolio(Class<A> acceptanceBound, Environment environment) {
    this.acceptanceBound = acceptanceBound;
    this.environment = environment;
  }

  boolean isAllowed(Class<? extends OmegaAcceptance> acceptance) {
    return OmegaAcceptanceCast.isInstanceOf(acceptance, acceptanceBound);
  }

  Optional<Automaton<?, A>> box(Automaton<?, ?> automaton) {
    return Optional.of(OmegaAcceptanceCast.cast(automaton, acceptanceBound));
  }
}
