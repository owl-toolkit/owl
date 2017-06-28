package owl.translations.rabinizer;

import org.immutables.value.Value;
import owl.factories.Factories;

@Value.Immutable
public abstract class RabinizerConfiguration {
  @Value.Default
  public boolean computeAcceptance() {
    return true;
  }

  @Value.Default
  public boolean eager() {
    return true;
  }

  public abstract Factories factories();

  @Value.Default
  public boolean removeFormulaRepresentative() {
    return true;
  }

  @Value.Default
  public boolean supportBasedRelevantFormulaAnalysis() {
    return true;
  }
}
