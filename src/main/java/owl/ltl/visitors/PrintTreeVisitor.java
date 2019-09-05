package owl.ltl.visitors;
/*
import java.util.List;

import owl.ltl.Biconditional;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.NegOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

public class PrintTreeVisitor implements BinaryVisitor<Integer,String> {

  private final List<String> variableMapping;

  public PrintTreeVisitor(List<String> v) {
    variableMapping = v;
  }

  public PrintTreeVisitor() {
    variableMapping = null;
  }

  @Override
  public String apply(Formula formula, Integer integer) {
    return formula.accept(this,integer);
  }

  @Override
  public String visit(Biconditional biconditional, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("\t(");
    out.append(biconditional.left.accept(this,parameter + 1));
    out.append(")\n");
    out.append(tab);
    out.append(" <-> \n");
    out.append(tab);
    out.append("\t(");
    out.append(biconditional.right.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(BooleanConstant booleanConstant, Integer parameter) {
    return booleanConstant.toString();
  }

  @Override
  public String visit(Conjunction conjunction, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    int i = 0;

    for (Formula f : conjunction.children) {
      out.append("\t(");
      out.append(f.accept(this,parameter + 1));
      out.append(")");
      if (i < conjunction.children.size() - 1) {
        out.append("\n");
        out.append(tab);
        out.append("&\n");
        out.append(tab);
        i++;
      }
    }
    return out.toString();
  }

  @Override
  public String visit(Disjunction disjunction, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    int i = 0;

    for (Formula f : disjunction.children) {
      out.append("\t(");
      out.append(f.accept(this,parameter + 1));
      out.append(")");
      if (i < disjunction.children.size() - 1) {
        out.append("\n");
        out.append(tab);
        out.append("|\n");
        out.append(tab);
        i++;
      }
    }
    return out.toString();
  }

  @Override
  public String visit(FOperator fOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("F(\n");
    out.append(tab);
    out.append("\t");
    out.append(fOperator.operand.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(GOperator gOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("G(\n");
    out.append(tab);
    out.append("\t");
    out.append(gOperator.operand.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(Literal literal,Integer parameter) {
    String name = variableMapping == null
      ? "p" + literal.getAtom()
      : variableMapping.get(literal.getAtom());
    return literal.isNegated() ? '!' + name : name;
  }

  @Override
  public String visit(MOperator mOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("\t(");
    out.append(mOperator.left.accept(this,parameter + 1));
    out.append(")\n");
    out.append(tab);
    out.append(" M \n");
    out.append(tab);
    out.append("\t(");
    out.append(mOperator.right.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(UOperator uOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("\t(");
    out.append(uOperator.left.accept(this,parameter + 1));
    out.append(")\n");
    out.append(tab);
    out.append(" U \n");
    out.append(tab);
    out.append("\t(");
    out.append(uOperator.right.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(ROperator rOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("\t(");
    out.append(rOperator.left.accept(this,parameter + 1));
    out.append(")\n");
    out.append(tab);
    out.append(" R \n");
    out.append(tab);
    out.append("\t(");
    out.append(rOperator.right.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(WOperator wOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("\t(");
    out.append(wOperator.left.accept(this,parameter + 1));
    out.append(")\n");
    out.append(tab);
    out.append(" W \n");
    out.append(tab);
    out.append("\t(");
    out.append(wOperator.right.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(XOperator xOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("X(\n");
    out.append(tab);
    out.append("\t");
    out.append(xOperator.operand.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

  @Override
  public String visit(NegOperator negOperator, Integer parameter) {
    StringBuilder tab = new StringBuilder();
    tab.append("\t".repeat(Math.max(0, parameter)));
    StringBuilder out = new StringBuilder();
    out.append("!(\n");
    out.append(tab);
    out.append("\t");
    out.append(negOperator.operand.accept(this,parameter + 1));
    out.append(")");
    return out.toString();
  }

}*/

