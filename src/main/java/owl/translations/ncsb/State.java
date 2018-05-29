package owl.translations.ncsb;

import com.google.common.collect.Sets;
import java.util.Set;
import org.immutables.value.Value;
import owl.util.annotation.HashedTuple;

@HashedTuple
@Value.Immutable
public abstract class State<S> {
  abstract Set<S> n(); // NOPMD Naming

  abstract Set<S> c(); // NOPMD Naming

  abstract Set<S> s(); // NOPMD Naming

  abstract Set<S> b(); // NOPMD Naming


  static <S> State<S> of(Set<S> n, Set<S> c, Set<S> s, Set<S> b) {
    return StateTuple.create(n, c, s, b);
  }

  static <S> State<S> of(Set<S> n) {
    return StateTuple.create(n, Set.of(), Set.of(), Set.of());
  }

  @Override
  public String toString() {
    return "(" + n() + ", " + c() + ", " + s() + ", " + b() + ')';
  }

  @Value.Check
  protected void check() {
    assert Sets.intersection(c(), s()).isEmpty();
  }
}
