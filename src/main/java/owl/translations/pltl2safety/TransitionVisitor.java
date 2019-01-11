package owl.translations.pltl2safety;

import java.util.Set;

import owl.ltl.Formula.TemporalOperator;
import owl.ltl.HOperator;
import owl.ltl.OOperator;
import owl.ltl.SOperator;
import owl.ltl.TOperator;
import owl.ltl.YOperator;
import owl.ltl.ZOperator;
import owl.ltl.visitors.Visitor;

public class TransitionVisitor implements Visitor<Boolean> {
  private final Set<TemporalOperator> state;
  private final Set<TemporalOperator> suc;

  TransitionVisitor(Set<TemporalOperator> state, Set<TemporalOperator> suc) {
    this.state = state;
    this.suc = suc;
  }

  @Override
  public Boolean visit(HOperator hOperator) {
    TemporalOperator nextOp = (TemporalOperator) hOperator.operand;
    return suc.contains(hOperator) == (suc.contains(nextOp) && state.contains(hOperator));
  }

  @Override
  public Boolean visit(OOperator oOperator) {
    TemporalOperator nextOp = (TemporalOperator) oOperator.operand;
    return suc.contains(oOperator) == (suc.contains(nextOp) || state.contains(oOperator));
  }

  @Override
  public Boolean visit(SOperator sOperator) {
    TemporalOperator left = (TemporalOperator) sOperator.left;
    TemporalOperator right = (TemporalOperator) sOperator.right;
    return suc.contains(sOperator)
      == (suc.contains(right) || (suc.contains(left) && state.contains(sOperator)));
  }

  @Override
  public Boolean visit(TOperator tOperator) {
    //TODO: check for correctness
    //a T b = !(!a S !b), transition of a S b is S' = b' | (a' & S)
    // => !(!b' | (!a' & S) = b' & (a' | ! S)
    TemporalOperator left = (TemporalOperator) tOperator.left;
    TemporalOperator right = (TemporalOperator) tOperator.right;
    return suc.contains(tOperator)
      == (suc.contains(right) && (suc.contains(left) || !state.contains(tOperator)));
  }

  @Override
  public Boolean visit(YOperator yOperator) {
    TemporalOperator nextOp = (TemporalOperator) yOperator.operand;
    return suc.contains(yOperator) == state.contains(nextOp);
  }

  @Override
  public Boolean visit(ZOperator zOperator) {
    TemporalOperator nextOp = (TemporalOperator) zOperator.operand;
    return suc.contains(zOperator) == state.contains(nextOp);
  }
}
