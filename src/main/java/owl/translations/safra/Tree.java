package owl.translations.safra;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.immutables.value.Value;
import owl.util.annotation.HashedTuple;

@HashedTuple
@Value.Immutable
public abstract class Tree<L> {
  abstract L label();

  abstract List<Tree<L>> children();


  static <L> Tree<L> of(L label) {
    return TreeTuple.create(label, List.of());
  }

  static <L> Tree<L> of(L label, List<Tree<L>> children) {
    return TreeTuple.create(label, children);
  }


  public Tree<L> with(L label) {
    return of(label, children());
  }


  public Tree<L> map(BiFunction<L, List<Tree<L>>, Tree<L>> function) {
    return function.apply(label(), Lists.transform(children(), child -> child.map(function)));
  }

  public Tree<L> map(Function<L, L> function) {
    return of(function.apply(label()), Lists.transform(children(), child -> child.map(function)));
  }


  public void forEach(Consumer<L> action) {
    children().forEach(child -> child.forEach(action));
    action.accept(this.label());
  }

  public void forEach(BiConsumer<L, List<L>> action) {
    action.accept(label(), Lists.transform(children(), Tree::label));
    children().forEach(child -> child.forEach(action));
  }


  @Override
  public String toString() {
    return children().isEmpty() ? "(" + label() + ')' : "(" + label() + " - " + children() + ')';
  }
}
