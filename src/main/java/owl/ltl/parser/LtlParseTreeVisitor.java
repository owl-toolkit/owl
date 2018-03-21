package owl.ltl.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.TerminalNode;
import owl.grammar.LTLParser.AndExpressionContext;
import owl.grammar.LTLParser.BinaryOpContext;
import owl.grammar.LTLParser.BinaryOperationContext;
import owl.grammar.LTLParser.BoolContext;
import owl.grammar.LTLParser.BooleanContext;
import owl.grammar.LTLParser.ExpressionContext;
import owl.grammar.LTLParser.FormulaContext;
import owl.grammar.LTLParser.FractionContext;
import owl.grammar.LTLParser.FrequencyOpContext;
import owl.grammar.LTLParser.NestedContext;
import owl.grammar.LTLParser.OrExpressionContext;
import owl.grammar.LTLParser.ProbabilityContext;
import owl.grammar.LTLParser.UnaryOpContext;
import owl.grammar.LTLParser.UnaryOperationContext;
import owl.grammar.LTLParser.VariableContext;
import owl.grammar.LTLParserBaseVisitor;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.FrequencyG;
import owl.ltl.FrequencyG.Limes;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

final class LtlParseTreeVisitor extends LTLParserBaseVisitor<Formula> {
  private final List<Literal> literalCache;
  private final List<String> variables;
  private final boolean fixedVariables;

  LtlParseTreeVisitor() {
    literalCache = new ArrayList<>();
    variables = new ArrayList<>();
    fixedVariables = false;
  }

  LtlParseTreeVisitor(List<String> literals) {
    ListIterator<String> literalIterator = literals.listIterator();
    List<Literal> literalList = new ArrayList<>();
    List<String> variableList = new ArrayList<>();

    while (literalIterator.hasNext()) {
      int index = literalIterator.nextIndex();
      String name = literalIterator.next();
      literalList.add(new Literal(index));
      variableList.add(name);
    }

    literalCache = List.copyOf(literalList);
    variables = List.copyOf(variableList);
    fixedVariables = true;
  }

  public List<String> variables() {
    return List.copyOf(variables);
  }

  @Override
  public Formula visitAndExpression(AndExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    return Conjunction.of(ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit));
  }

  @Override
  public Formula visitBinaryOperation(BinaryOperationContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    BinaryOpContext binaryOp = ctx.binaryOp();
    Formula left = visit(ctx.left);
    Formula right = visit(ctx.right);

    if (binaryOp.BIIMP() != null) {
      return Disjunction.of(Conjunction.of(left, right),
        Conjunction.of(left.not(), right.not()));
    }

    if (binaryOp.IMP() != null) {
      return Disjunction.of(left.not(), right);
    }

    if (binaryOp.XOR() != null) {
      return Disjunction.of(Conjunction.of(left, right.not()),
        Conjunction.of(left.not(), right));
    }

    if (binaryOp.UNTIL() != null) {
      return UOperator.of(left, right);
    }

    if (binaryOp.WUNTIL() != null) {
      return WOperator.of(left, right);
    }

    if (binaryOp.RELEASE() != null) {
      return ROperator.of(left, right);
    }

    if (binaryOp.SRELEASE() != null) {
      return MOperator.of(left, right);
    }

    throw new ParseCancellationException("Unknown operator");
  }

  @Override
  public Formula visitBoolean(BooleanContext ctx) {
    assert ctx.getChildCount() == 1;
    BoolContext constant = ctx.bool();

    if (constant.FALSE() != null) {
      return BooleanConstant.FALSE;
    }

    if (constant.TRUE() != null) {
      return BooleanConstant.TRUE;
    }

    throw new ParseCancellationException("Unknown constant");
  }

  @Override
  public Formula visitExpression(ExpressionContext ctx) {
    assert ctx.getChildCount() == 1;
    return visit(ctx.getChild(0));
  }

  @Override
  public Formula visitFormula(FormulaContext ctx) {
    // Contained formula + EOF
    assert ctx.getChildCount() == 2 : ctx.getChildCount();
    return visit(ctx.getChild(0));
  }

  @Override
  public Formula visitNested(NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.nested);
  }

  @Override
  public Formula visitOrExpression(OrExpressionContext ctx) {
    assert ctx.getChildCount() > 0;

    return Disjunction.of(ctx.children.stream()
      .filter(child -> !(child instanceof TerminalNode))
      .map(this::visit));
  }

  @Override
  @SuppressWarnings("PMD.ConfusingTernary")
  public Formula visitUnaryOperation(UnaryOperationContext ctx) {
    assert ctx.getChildCount() == 2;
    UnaryOpContext unaryOp = ctx.unaryOp();
    Formula operand = visit(ctx.inner);

    if (unaryOp.NOT() != null) {
      return operand.not();
    }

    if (unaryOp.FINALLY() != null) {
      return FOperator.of(operand);
    }

    if (unaryOp.GLOBALLY() != null) {
      return GOperator.of(operand);
    }

    if (unaryOp.NEXT() != null) {
      return XOperator.of(operand);
    }

    if (unaryOp.frequencyOp() != null) {
      FrequencyOpContext freqCtx = unaryOp.frequencyOp();
      assert freqCtx.op != null && freqCtx.comp != null && freqCtx.prob != null;
      FrequencyG.Comparison comparison;
      boolean negateComparison;
      if (freqCtx.comp.GE() != null) {
        comparison = FrequencyG.Comparison.GEQ;
        negateComparison = false;
      } else if (freqCtx.comp.GT() != null) {
        comparison = FrequencyG.Comparison.GT;
        negateComparison = false;
      } else if (freqCtx.comp.LE() != null) {
        comparison = FrequencyG.Comparison.GT;
        negateComparison = true;
      } else if (freqCtx.comp.LT() != null) {
        comparison = FrequencyG.Comparison.GEQ;
        negateComparison = true;
      } else {
        throw new ParseCancellationException("Unknown comparison");
      }

      boolean negateOperator;
      //noinspection StatementWithEmptyBody
      if (freqCtx.GLOBALLY() != null) {
        negateOperator = false;
      } else if (freqCtx.FINALLY() != null) {
        negateOperator = true;
      } else {
        throw new ParseCancellationException("Unknown operator");
      }

      double value;
      if (freqCtx.prob instanceof FractionContext) {
        FractionContext fraction = (FractionContext) freqCtx.prob;
        String numeratorString = fraction.numerator.getText();
        String denominatorString = fraction.denominator.getText();
        double numerator;
        double denominator;
        try {
          numerator = Double.parseDouble(numeratorString);
          denominator = Double.parseDouble(denominatorString);
        } catch (NumberFormatException e) {
          throw new ParseCancellationException("Invalid numbers", e);
        }
        value = numerator / denominator;
        if (value < 0d || 1d < value) {
          throw new ParseCancellationException("Invalid numbers");
        }
      } else if (freqCtx.prob instanceof ProbabilityContext) {
        ProbabilityContext probability = (ProbabilityContext) freqCtx.prob;
        String valueString = probability.value.getText();
        value = Double.parseDouble(valueString);
      } else {
        throw new ParseCancellationException("Unknown frequency spec");
      }

      Limes limes;
      if (freqCtx.SUP() != null) {
        limes = Limes.SUP;
      } else {
        limes = Limes.INF;
      }

      Formula finalFormula;
      double finalValue;
      if (negateComparison == negateOperator) {
        finalFormula = operand;
        finalValue = value;
      } else {
        finalFormula = operand.not();
        finalValue = 1 - value;
      }

      return new FrequencyG(finalFormula, finalValue, comparison, limes);
    }
    assert false;
    return null;
  }

  @Override
  public Formula visitVariable(VariableContext ctx) {
    assert ctx.getChildCount() == 1;
    assert variables.size() == literalCache.size();
    String name = ctx.getText();
    int index = variables.indexOf(name);

    if (index == -1) {
      if (fixedVariables) {
        throw new IllegalStateException("Encountered unknown variable " + name
          + " with fixed set " + variables);
      }

      int newIndex = variables.size();
      Literal literal = new Literal(newIndex);
      variables.add(name);
      literalCache.add(literal);
      return literal;
    }

    assert index >= 0;
    return literalCache.get(index);
  }
}
