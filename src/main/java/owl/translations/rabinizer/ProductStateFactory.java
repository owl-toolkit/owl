package owl.translations.rabinizer;

import java.util.BitSet;
import owl.ltl.EquivalenceClass;

class ProductStateFactory extends RabinizerStateFactory {
  ProductStateFactory(boolean eager) {
    super(eager);
  }

  public BitSet getSensitiveAlphabet(RabinizerState state) {
    BitSet sensitiveAlphabet = getClassSensitiveAlphabet(state.masterState);
    for (MonitorState monitorState : state.monitorStates) {
      for (EquivalenceClass rankedFormula : monitorState.formulaRanking) {
        sensitiveAlphabet.or(getClassSensitiveAlphabet(rankedFormula));
      }
    }
    return sensitiveAlphabet;
  }
}
