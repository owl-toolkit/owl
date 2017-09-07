package owl.translations.rabinizer;

import java.util.BitSet;
import owl.ltl.EquivalenceClass;

class RabinizerStateFactory {
  final boolean eager;

  RabinizerStateFactory(boolean eager) {
    this.eager = eager;
  }

  void addClassSensitiveAlphabet(BitSet alphabet, EquivalenceClass equivalenceClass) {
    if (eager) {
      alphabet.or(equivalenceClass.getAtoms());
    } else {
      EquivalenceClass unfold = equivalenceClass.unfold();
      alphabet.or(unfold.getAtoms());
      unfold.free();
    }
  }
}
