package owl.translations.nba2ldba;

import com.google.common.collect.ImmutableSet;

import java.util.Objects;
import java.util.Set;

public class BreakpointState<S> {

  final int ix;
  final Set<S> mx;
  final Set<S> nx;

  BreakpointState(int i, Set<S> m, Set<S> n) {
    this.ix = i;
    this.mx = ImmutableSet.copyOf(m);
    this.nx = ImmutableSet.copyOf(n);
  }

  BreakpointState(S state) {
    this.ix = -1;
    this.mx = ImmutableSet.of(state);
    this.nx = ImmutableSet.of(state);
  }

  public String toString() {
    return "(" + ix + ", " + this.mx + ", " + this.nx + ")";
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    BreakpointState<?> state = (BreakpointState<?>) o;
    return ix == state.ix && Objects.equals(mx, state.mx)
        && Objects.equals(nx, state.nx);
  }

  @Override
  public int hashCode() {
    return 31 * mx.hashCode() + nx.hashCode() + ix;
  }

}
