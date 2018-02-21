package owl.translations.rabinizer;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.immutables.value.Value;
import owl.ltl.EquivalenceClass;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class RabinizerState {
  public abstract EquivalenceClass masterState();

  public abstract List<MonitorState> monitorStates();


  static RabinizerState of(EquivalenceClass masterState, MonitorState[] monitorStates) {
    return RabinizerStateTuple.create(masterState, ImmutableList.copyOf(monitorStates));
  }

  static RabinizerState of(EquivalenceClass masterState, List<MonitorState> monitorStates) {
    return RabinizerStateTuple.create(masterState, monitorStates);
  }

  static RabinizerState empty(EquivalenceClass masterState) {
    return RabinizerStateTuple.create(masterState, List.of());
  }


  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(50 * (1 + monitorStates().size()));
    builder.append("<<").append(masterState());
    for (MonitorState monitorState : monitorStates()) {
      builder.append("::").append(monitorState);
    }
    builder.append(">>");
    return builder.toString();
  }
}
