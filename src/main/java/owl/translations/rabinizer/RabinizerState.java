package owl.translations.rabinizer;

import java.util.Arrays;
import javax.annotation.concurrent.Immutable;
import owl.ltl.EquivalenceClass;

@Immutable
public final class RabinizerState {
  final EquivalenceClass masterState;
  final MonitorState[] monitorStates;
  private final int hashCode;

  @SuppressWarnings({"PMD.ArrayIsStoredDirectly",
                      "AssignmentToCollectionOrArrayFieldFromParameter"})
  RabinizerState(EquivalenceClass masterState, MonitorState[] monitorStates) {
    this.masterState = masterState;
    this.monitorStates = monitorStates;
    hashCode = masterState.hashCode() ^ Arrays.hashCode(monitorStates);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof RabinizerState)) {
      return false;
    }

    RabinizerState that = (RabinizerState) o;
    return masterState.equals(that.masterState)
      && Arrays.equals(monitorStates, that.monitorStates);
  }

  @Override
  public int hashCode() {
    return hashCode;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder(50 * (1 + monitorStates.length));
    builder.append("<<").append(masterState);
    for (MonitorState monitorState : monitorStates) {
      builder.append("::").append(monitorState);
    }
    builder.append(">>");
    return builder.toString();
  }
}
