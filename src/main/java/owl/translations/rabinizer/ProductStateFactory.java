package owl.translations.rabinizer;

import java.util.BitSet;
import owl.ltl.EquivalenceClass;

class ProductStateFactory extends RabinizerStateFactory {
  ProductStateFactory(boolean eager) {
    super(eager);
  }

  public void addSensitiveAlphabet(BitSet sensitiveAlphabet, RabinizerState state) {
    addClassSensitiveAlphabet(sensitiveAlphabet, state.masterState);
    for (MonitorState monitorState : state.monitorStates) {
      for (EquivalenceClass rankedFormula : monitorState.formulaRanking) {
        addClassSensitiveAlphabet(sensitiveAlphabet, rankedFormula);
      }
    }
  }
}
