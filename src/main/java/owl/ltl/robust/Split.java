package owl.ltl.robust;

import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;
import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.Formula;
import owl.util.annotation.Tuple;

@Tuple
@Value.Immutable
public abstract class Split {

  public static final Split TRUE = of(BooleanConstant.TRUE, true);

  public static final Split FALSE = of(BooleanConstant.FALSE, true);


  public abstract Formula always();

  public abstract Formula eventuallyAlways();

  public abstract Formula infinitelyOften();

  public abstract Formula eventually();

  @Value.Auxiliary
  abstract boolean grFree();


  static Split of(Formula formula, boolean grFree) {
    return SplitTuple.create(formula, formula, formula, formula, grFree);
  }

  static Split of(Formula first, Formula second, Formula third, Formula fourth,
    boolean grFree) {
    return SplitTuple.create(first, second, third, fourth, grFree);
  }

  public static BinaryOperator<Split> combiner(BinaryOperator<Formula> formulaCombiner) {
    return (one, other) -> of(formulaCombiner.apply(one.always(), other.always()),
      formulaCombiner.apply(one.eventuallyAlways(), other.eventuallyAlways()),
      formulaCombiner.apply(one.infinitelyOften(), other.infinitelyOften()),
      formulaCombiner.apply(one.eventually(), other.eventually()),
      one.grFree() && other.grFree());
  }


  public Split map(UnaryOperator<Formula> map) {
    return map(map, grFree());
  }

  public Split map(UnaryOperator<Formula> map, boolean grFree) {
    return of(map.apply(always()), map.apply(eventuallyAlways()), map.apply(infinitelyOften()),
      map.apply(eventually()), grFree);
  }


  public List<Formula> all() {
    return List.of(always(), eventuallyAlways(), infinitelyOften(), eventually());
  }

  public Formula get(Robustness level) {
    switch (level) {
      case ALWAYS:
        return always();
      case EVENTUALLY_ALWAYS:
        return eventuallyAlways();
      case INFINITELY_OFTEN:
        return infinitelyOften();
      case EVENTUALLY:
        return eventually();
      case NEVER:
        return eventually().not();
      default:
        throw new AssertionError();
    }
  }

  @Override
  public String toString() {
    return "G: " + always() + " FG: " + eventuallyAlways() + " GF: " + infinitelyOften()
      + " F: " + eventually();
  }
}
