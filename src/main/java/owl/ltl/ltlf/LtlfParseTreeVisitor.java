package owl.ltl.ltlf;

/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
 *
 * This file is part of Owl.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
import owl.grammar.LTLParser.DoubleQuotedVariableContext;
import owl.grammar.LTLParser.ExpressionContext;
import owl.grammar.LTLParser.FormulaContext;
import owl.grammar.LTLParser.FractionContext;
import owl.grammar.LTLParser.FrequencyOpContext;
import owl.grammar.LTLParser.NestedContext;
import owl.grammar.LTLParser.OrExpressionContext;
import owl.grammar.LTLParser.ProbabilityContext;
import owl.grammar.LTLParser.SingleQuotedVariableContext;
import owl.grammar.LTLParser.UnaryOpContext;
import owl.grammar.LTLParser.UnaryOperationContext;
import owl.grammar.LTLParser.VariableContext;
import owl.grammar.LTLParserBaseVisitor;
import owl.ltl.Biconditional;
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
import owl.ltl.NegOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;

final class LtlfParseTreeVisitor extends LTLParserBaseVisitor<Formula> {
  private final List<Literal> literalCache;
  private final List<String> variables;
  private final boolean fixedVariables;

  LtlfParseTreeVisitor() {
    literalCache = new ArrayList<>();
    variables = new ArrayList<>();
    fixedVariables = false;
  }

  LtlfParseTreeVisitor(List<String> literals) {
    ListIterator<String> literalIterator = literals.listIterator();
    List<Literal> literalList = new ArrayList<>();
    List<String> variableList = new ArrayList<>();

    while (literalIterator.hasNext()) {
      int index = literalIterator.nextIndex();
      String name = literalIterator.next();
      literalList.add(Literal.of(index));
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
    if (ctx.getChildCount() == 1) { //avoid Conjunctions with only one child
      return ctx.getChild(0).accept(this);
    }
    return Conjunction.syntaxConjunction(ctx.children.stream()
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
      return new Biconditional(left, right);
    }

    if (binaryOp.IMP() != null) {
      return Disjunction.syntaxDisjunction(new NegOperator(left), right);
    }

    if (binaryOp.XOR() != null) {
      return new Biconditional(left.not(), right);
    }

    if (binaryOp.UNTIL() != null) {
      return new UOperator(left, right);
    }

    if (binaryOp.WUNTIL() != null) {
      return new WOperator(left, right);
    }

    if (binaryOp.RELEASE() != null) {
      return new ROperator(left, right);
    }

    if (binaryOp.SRELEASE() != null) {
      return new MOperator(left, right);
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
    if (ctx.getChildCount() == 1) { //avoid Disjunctions with only one child
      return ctx.getChild(0).accept(this);
    }
    return Disjunction.syntaxDisjunction(ctx.children.stream()
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
      return new NegOperator(operand);
    }

    if (unaryOp.FINALLY() != null) {
      return new FOperator(operand);
    }

    if (unaryOp.GLOBALLY() != null) {
      return new GOperator(operand);
    }

    if (unaryOp.NEXT() != null) {
      return new XOperator(operand);
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

    throw new AssertionError("Unreachable Code");
  }

  @Override
  public Formula visitVariable(VariableContext ctx) {
    assert ctx.getChildCount() == 1;
    return createVariable(ctx.getText());
  }

  @Override
  public Formula visitSingleQuotedVariable(SingleQuotedVariableContext ctx) {
    assert ctx.getChildCount() == 3;
    return createVariable(ctx.variable.getText());
  }

  @Override
  public Formula visitDoubleQuotedVariable(DoubleQuotedVariableContext ctx) {
    assert ctx.getChildCount() == 3;
    return createVariable(ctx.variable.getText());
  }

  private Formula createVariable(String name) {
    assert variables.size() == literalCache.size();
    int index = variables.indexOf(name);

    if (index == -1) {
      if (fixedVariables) {
        throw new IllegalStateException("Encountered unknown variable " + name
          + " with fixed set " + variables);
      }

      int newIndex = variables.size();
      Literal literal = Literal.of(newIndex);
      variables.add(name);
      literalCache.add(literal);
      return literal;
    }

    return literalCache.get(index);
  }
}
