package owl.translations.rabinizer;

import java.util.BitSet;
import owl.ltl.EquivalenceClass;

class RabinizerStateFactory {
  final boolean eager;

  RabinizerStateFactory(boolean eager) {
    this.eager = eager;
  }

  BitSet getClassSensitiveAlphabet(EquivalenceClass equivalenceClass) {
    if (eager) {
      return equivalenceClass.getAtoms();
    }

    return equivalenceClass.unfold().getAtoms();
  }
}
