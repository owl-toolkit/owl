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

package owl.ltl.parser;

import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.grammar.SPECTRAParser.AdditiveContext;
import owl.grammar.SPECTRAParser.ConstContext;
import owl.grammar.SPECTRAParser.ConstantContext;
import owl.grammar.SPECTRAParser.MultiplicativeContext;
import owl.grammar.SPECTRAParser.NegateContext;
import owl.grammar.SPECTRAParser.NestedContext;
import owl.grammar.SPECTRAParser.PredPattContext;
import owl.grammar.SPECTRAParser.PrimaryContext;
import owl.grammar.SPECTRAParser.ReferableContext;
import owl.grammar.SPECTRAParser.RelationalContext;
import owl.grammar.SPECTRAParser.RelationalOpContext;
import owl.grammar.SPECTRAParser.RemainderContext;
import owl.grammar.SPECTRAParser.SpecialNextContext;
import owl.grammar.SPECTRAParserBaseVisitor;
import owl.ltl.parser.spectra.expressios.EqualsExpression;
import owl.ltl.parser.spectra.expressios.HigherOrderExpression;
import owl.ltl.parser.spectra.expressios.LessThanExpression;
import owl.ltl.parser.spectra.expressios.LessThanOrEqualsExpression;
import owl.ltl.parser.spectra.expressios.NegateExpression;
import owl.ltl.parser.spectra.expressios.NotEqualsExpression;
import owl.ltl.parser.spectra.expressios.SpecialNextExpression;
import owl.ltl.parser.spectra.expressios.variables.SpectraArrayVariable;
import owl.ltl.parser.spectra.types.SpectraType;

final class SpectraExpressionVisitor extends SPECTRAParserBaseVisitor<HigherOrderExpression> {
  private final Map<String, HigherOrderExpression> variables;
  private final SpectraType expressionType;
  private final Set<String> typeConstants;

  SpectraExpressionVisitor(Map<String, HigherOrderExpression> variables,
                           Set<String> typeConstants, SpectraType expressionType) {
    this.variables = variables;
    this.typeConstants = typeConstants;
    this.expressionType = expressionType;
  }

  @Override
  public HigherOrderExpression visitRelational(RelationalContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    RelationalOpContext relationalOp = ctx.relationalOp();
    HigherOrderExpression left = visit(ctx.left);
    HigherOrderExpression right = visit(ctx.right);

    if (relationalOp.EQ() != null) {
      return new EqualsExpression(left, right);
    }

    if (relationalOp.NE() != null) {
      return new NotEqualsExpression(left, right);
    }

    if (relationalOp.LT() != null) {
      return new LessThanExpression(left, right);
    }

    if (relationalOp.LE() != null) {
      return new LessThanOrEqualsExpression(left, right);
    }

    if (relationalOp.GT() != null) {
      return new LessThanExpression(right, left);
    }

    if (relationalOp.GE() != null) {
      return new LessThanOrEqualsExpression(right, left);
    }

    throw new ParseCancellationException("Unrecognizable relational operator");
  }

  @Override
  public HigherOrderExpression visitRemainder(RemainderContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    throw new ParseCancellationException("Mod is an unsupported operator as of yet");
  }

  @Override
  public HigherOrderExpression visitAdditive(AdditiveContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    throw new ParseCancellationException("Add/Minus are unsupported operators as of yet");
  }

  @Override
  public HigherOrderExpression visitMultiplicative(MultiplicativeContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    throw new ParseCancellationException("Mul/Div are unsupported operators as of yet");
  }
  
  @Override
  public HigherOrderExpression visitPrimary(PrimaryContext ctx) {
    assert ctx.getChildCount() == 1;
    return visit(ctx.temporalPrimaryExpr());
  }

  @Override
  public HigherOrderExpression visitConst(ConstContext ctx) {
    assert ctx.getChildCount() == 1;
    ConstantContext constant = ctx.constant();

    if (constant.FALSE() != null) {
      return expressionType.of("false");
    }

    if (constant.TRUE() != null) {
      return expressionType.of("true");
    }

    if (constant.INT() != null) {
      return expressionType.of(ctx.getText());
    }

    throw new ParseCancellationException("Unknown constant");
  }

  @Override
  public HigherOrderExpression visitNested(NestedContext ctx) {
    assert ctx.getChildCount() == 3;
    return visit(ctx.temporalExpr());
  }

  @Override
  public HigherOrderExpression visitPredPatt(PredPattContext ctx) {
    //TODO: implement this
    throw new ParseCancellationException("Predicate and patterns not implemented yet");
  }

  @Override
  public HigherOrderExpression visitNegate(NegateContext ctx) {
    assert ctx.getChildCount() == 2;
    return new NegateExpression(visit(ctx.temporalPrimaryExpr()));
  }

  @Override
  public HigherOrderExpression visitReferable(ReferableContext ctx) {
    //TODO: cross reference
    if (variables.containsKey(ctx.pointer.getText())) {
      HigherOrderExpression component = variables.get(ctx.pointer.getText());
      int[] dims = ctx.intvalue.stream()
        .mapToInt(dim -> Integer.parseInt(dim.getText())).toArray();
      if (dims.length > 0) {
        return ((SpectraArrayVariable) component).of(dims);
      } else {
        return component;
      }
    } else if (typeConstants.contains(ctx.pointer.getText())) {
      return expressionType.of(ctx.pointer.getText());
    } else {
      throw new ParseCancellationException("Unsupported cross reference as of now");
    }
  }

  @Override
  public HigherOrderExpression visitSpecialNext(SpecialNextContext ctx) {
    assert ctx.getChildCount() == 4;

    return new SpecialNextExpression(visit(ctx.temporalExpr()));
  }
}
