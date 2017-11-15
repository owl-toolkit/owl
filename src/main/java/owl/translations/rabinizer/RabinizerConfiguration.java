package owl.translations.rabinizer;

import org.immutables.value.Value;

@Value.Immutable
public class RabinizerConfiguration { // NOPMD
  @Value.Default
  public boolean completeAutomaton() {
    return false;
  }

  @Value.Default
  public boolean computeAcceptance() {
    return true;
  }

  @Value.Default
  public boolean eager() {
    return true;
  }

  @Value.Default
  public boolean supportBasedRelevantFormulaAnalysis() {
    return true;
  }

  @Value.Default
  public boolean suspendableFormulaDetection() {
    return true;
  }

  @Value.Default
  public boolean removeFormulaRepresentative() {
    return false;
  }
}
