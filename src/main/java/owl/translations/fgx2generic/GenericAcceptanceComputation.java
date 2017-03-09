package owl.translations.fgx2generic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jhoafparser.ast.AtomAcceptance;
import jhoafparser.ast.BooleanExpression;
import owl.collections.ValuationSet;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.DefaultVisitor;
import owl.ltl.visitors.HistoryValuationSetVisitor;
import owl.ltl.visitors.predicates.XFragment;

class GenericAcceptanceComputation
  extends DefaultVisitor<BooleanExpression<AtomAcceptance>> {

  private final Map<List<ValuationSet>, Integer> sets;
  private final HistoryValuationSetVisitor visitor;

  public Map<List<ValuationSet>, Integer> getSets() {
    return sets;
  }

  GenericAcceptanceComputation(Factories factories, int historyLength) {
    this.visitor = new HistoryValuationSetVisitor(factories.valuationSetFactory,
      historyLength + 1);
    sets = new HashMap<>();
  }

  @Override
  protected BooleanExpression<AtomAcceptance> defaultAction(Formula formula) {
    throw new IllegalArgumentException(formula.toString());
  }

  private int getAccepanceSet(Formula xFragment) {
    assert XFragment.testStatic(xFragment);
    List<ValuationSet> set = RewriterFactory.apply(RewriterEnum.PUSHDOWN_X, xFragment)
      .accept(visitor);
    return sets.computeIfAbsent(set, (x) -> sets.size());
  }

  BooleanExpression<AtomAcceptance> mkFin(Formula formula) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_FIN, getAccepanceSet(formula), false));
  }

  BooleanExpression<AtomAcceptance> mkInf(Formula formula) {
    return new BooleanExpression<>(
      new AtomAcceptance(AtomAcceptance.Type.TEMPORAL_INF, getAccepanceSet(formula), false));
  }

  @Override
  public BooleanExpression<AtomAcceptance> visit(BooleanConstant booleanConstant) {
    return new BooleanExpression<>(booleanConstant.value);
  }

  @Override
  public BooleanExpression<AtomAcceptance> visit(Conjunction conjunction) {
    return conjunction.map(x -> x.accept(this)).reduce(BooleanExpression::and)
      .orElseGet(() -> new BooleanExpression<>(true));
  }

  @Override
  public BooleanExpression<AtomAcceptance> visit(Disjunction disjunction) {
    return disjunction.map(x -> x.accept(this)).reduce(BooleanExpression::or)
      .orElseGet(() -> new BooleanExpression<>(false));
  }

  @Override
  public BooleanExpression<AtomAcceptance> visit(FOperator fOperator) {
    return mkFin(((GOperator) fOperator.operand).operand.not());
  }

  @Override
  public BooleanExpression<AtomAcceptance> visit(GOperator gOperator) {
    return mkInf(((FOperator) gOperator.operand).operand);
  }
}
