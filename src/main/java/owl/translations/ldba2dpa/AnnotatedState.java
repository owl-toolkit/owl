package owl.translations.ldba2dpa;

import javax.annotation.Nullable;
import owl.util.ImmutableObject;

public abstract class AnnotatedState<S> extends ImmutableObject {
  @Nullable
  public final S state;

  protected AnnotatedState(@Nullable S state) {
    this.state = state;
  }
}
