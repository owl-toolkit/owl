package owl.ltl.visitors;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import javax.annotation.Nonnegative;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.PropositionalFormula;
import owl.ltl.XOperator;

public class XDepthVisitor implements IntVisitor {

  private static final Object2IntMap<Formula> CACHE = new Object2IntOpenHashMap<>();
  private static final XDepthVisitor INSTANCE = new XDepthVisitor();

  @Nonnegative
  public static int getDepth(Formula formula) {
    if (CACHE.size() > 1024) {
      CACHE.clear();
    }

    return CACHE.computeIfAbsent(formula, x -> x.accept(INSTANCE));
  }

  @Nonnegative
  @Override
  public int visit(FOperator fOperator) {
    return fOperator.operand.accept(this);
  }

  @Nonnegative
  @Override
  public int visit(GOperator gOperator) {
    return gOperator.operand.accept(this);
  }

  @Nonnegative
  @Override
  public int visit(BooleanConstant booleanConstant) {
    return 0;
  }

  @Nonnegative
  @Override
  public int visit(Conjunction conjunction) {
    return visit((PropositionalFormula) conjunction);
  }

  @Nonnegative
  @Override
  public int visit(Disjunction disjunction) {
    return visit((PropositionalFormula) disjunction);
  }

  @Nonnegative
  @Override
  public int visit(Literal literal) {
    return 0;
  }

  @Nonnegative
  @Override
  public int visit(XOperator xOperator) {
    return xOperator.operand.accept(this) + 1;
  }

  @Nonnegative
  private int visit(PropositionalFormula formula) {
    int max = 0;

    for (Formula child : formula.children) {
      max = Math.max(max, child.accept(this));
    }

    return max;
  }
}
