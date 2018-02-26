package owl.ltl.visitors;

import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.LabelledFormula;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public final class PrintVisitor implements Visitor<String> {
  private final boolean parenthesize;
  @Nullable
  private final List<String> variableMapping;

  private PrintVisitor(boolean parenthesize, @Nullable List<String> variableMapping) {
    this.variableMapping = variableMapping;
    this.parenthesize = parenthesize;
  }

  public static String toString(Formula formula, List<String> variableMapping) {
    return toString(formula, variableMapping, false);
  }

  public static String toString(Formula formula, @Nullable List<String> variableMapping,
    boolean parenthesize) {
    PrintVisitor visitor = new PrintVisitor(parenthesize, variableMapping);
    return formula.accept(visitor);
  }

  public static String toString(LabelledFormula formula, boolean parenthesize) {
    PrintVisitor visitor = new PrintVisitor(parenthesize, formula.variables());
    return formula.accept(visitor);
  }

  @Override
  public String visit(BooleanConstant booleanConstant) {
    return booleanConstant.toString();
  }

  @Override
  public String visit(Conjunction conjunction) {
    return '(' + String.join(" & ", () -> conjunction.children.stream()
      .map((Function<Formula, CharSequence>) this::visitParenthesized).iterator()) + ')';
  }

  @Override
  public String visit(Disjunction disjunction) {
    return '(' + String.join(" | ", () -> disjunction.children.stream()
      .map((Function<Formula, CharSequence>) this::visitParenthesized).iterator()) + ')';
  }

  @Override
  public String visit(FOperator fOperator) {
    return visit((UnaryModalOperator) fOperator);
  }

  @Override
  public String visit(FrequencyG freq) {
    return "G {" + freq.limes + freq.cmp + freq.bound + "} " + freq.operand.accept(this);
  }

  @Override
  public String visit(GOperator gOperator) {
    return visit((UnaryModalOperator) gOperator);
  }

  @Override
  public String visit(Literal literal) {
    String name = variableMapping == null
      ? literal.toString()
      : variableMapping.get(literal.getAtom());
    return literal.isNegated() ? '!' + name : name;
  }

  @Override
  public String visit(MOperator mOperator) {
    return visit((BinaryModalOperator) mOperator);
  }

  @Override
  public String visit(ROperator rOperator) {
    return visit((BinaryModalOperator) rOperator);
  }

  @Override
  public String visit(UOperator uOperator) {
    return visit((BinaryModalOperator) uOperator);
  }

  @Override
  public String visit(WOperator wOperator) {
    return visit((BinaryModalOperator) wOperator);
  }

  @Override
  public String visit(XOperator xOperator) {
    return visit((UnaryModalOperator) xOperator);
  }

  private String visit(UnaryModalOperator operator) {
    return operator.getOperator() + visitParenthesized(operator.operand);
  }

  private String visit(BinaryModalOperator operator) {
    return "((" + visitParenthesized(operator.left) + ") "
      + operator.getOperator()
      + " (" + operator.right.accept(this) + "))";
  }

  private String visitParenthesized(Formula formula) {
    return parenthesize ? '(' + formula.accept(this) + ')' : formula.accept(this);
  }
}