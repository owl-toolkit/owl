package owl.translations.frequency;

import java.util.Map.Entry;
import java.util.Set;
import owl.ltl.FrequencyG;
import owl.translations.frequency.ProductControllerSynthesis.State;

public interface BoundAndRewardForPrism {
  FrequencyG getFreqG();

  Set<Entry<Integer, TranSet<State>>> relevantEntries();
}
