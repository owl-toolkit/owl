package owl.translations.fgx2dpa;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import org.immutables.value.Value;
import owl.ltl.BooleanConstant;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.UnaryModalOperator;
import owl.util.annotation.HashedTuple;

@Value.Immutable
@HashedTuple
public abstract class PromisedSet {

  abstract Set<GOperator> formulaeG();

  abstract List<FOperator> formulaeF();

  abstract Formula firstF();


  @Value.Auxiliary
  @Value.Derived
  long nonFinalGCount() {
    return formulaeG().stream().map(UnaryModalOperator::getOperand)
      .filter(operand -> !operand.accept(SafetyAutomaton.FinalStateVisitor.INSTANCE))
      .count();
  }

  @Value.Auxiliary
  @Value.Derived
  Set<UnaryModalOperator> union() {
    return Sets.union(formulaeG(), Set.copyOf(formulaeF()));
  }


  public static PromisedSet of(Set<GOperator> formulaeG, List<FOperator> formulaeF,
                               Formula firstF) {
    return PromisedSetTuple.create(formulaeG, formulaeF, firstF);
  }

  public static PromisedSet of(Set<GOperator> formulaeG, List<FOperator> formulaeF) {
    return of(formulaeG, formulaeF, Iterables.getFirst(formulaeF, BooleanConstant.TRUE));
  }
}
