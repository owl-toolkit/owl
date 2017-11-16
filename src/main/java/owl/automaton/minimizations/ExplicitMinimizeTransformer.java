package owl.automaton.minimizations;

import com.google.common.collect.ImmutableList;
import java.util.List;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.run.env.Environment;
import owl.run.transformer.Transformer;

public class ExplicitMinimizeTransformer<S, A extends OmegaAcceptance>
  implements Transformer.Factory {
  private final Class<A> acceptanceClass;
  private final List<Minimization<S, A>> minimizationList;
  private final Class<S> stateClass;

  public ExplicitMinimizeTransformer(List<Minimization<S, A>> minimizationList,
    Class<S> stateClass, Class<A> acceptanceClass) {
    this.minimizationList = ImmutableList.copyOf(minimizationList);
    this.stateClass = stateClass;
    this.acceptanceClass = acceptanceClass;
  }

  @Override
  public Transformer createTransformer(Environment environment) {
    return (input, context) -> {
      MutableAutomaton<S, A> castedAutomaton =
        AutomatonUtil.castMutable(input, stateClass, acceptanceClass);
      for (Minimization<S, A> minimization : minimizationList) {
        minimization.apply(castedAutomaton);
      }
      return castedAutomaton;
    };
  }
}