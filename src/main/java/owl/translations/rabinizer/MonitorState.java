package owl.translations.rabinizer;

import java.util.List;
import org.immutables.value.Value;
import owl.ltl.EquivalenceClass;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
abstract class MonitorState {
  abstract List<EquivalenceClass> formulaRanking();

  static MonitorState of(EquivalenceClass initialClass) {
    return MonitorStateTuple.create(List.of(initialClass));
  }

  static MonitorState of(List<EquivalenceClass> ranking) {
    return MonitorStateTuple.create(ranking);
  }
}
