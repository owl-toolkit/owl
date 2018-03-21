package owl.automaton.minimizations;

import java.util.List;
import owl.automaton.AutomatonUtil;
import owl.automaton.MutableAutomaton;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.run.PipelineExecutionContext;
import owl.run.modules.Transformer;

public class ExplicitMinimizeTransformer<S, A extends OmegaAcceptance>
  implements Transformer.Instance {
  private final Class<A> acceptanceClass;
  private final List<Minimization<S, A>> minimizationList;
  private final Class<S> stateClass;

  public ExplicitMinimizeTransformer(List<Minimization<S, A>> minimizationList,
    Class<S> stateClass, Class<A> acceptanceClass) {
    this.minimizationList = List.copyOf(minimizationList);
    this.stateClass = stateClass;
    this.acceptanceClass = acceptanceClass;
  }

  @Override
  public Object transform(Object object, PipelineExecutionContext context) {
    MutableAutomaton<S, A> castedAutomaton =
      AutomatonUtil.castMutable(object, stateClass, acceptanceClass);
    for (Minimization<S, A> minimization : minimizationList) {
      minimization.minimize(castedAutomaton);
    }
    return castedAutomaton;
  }
}