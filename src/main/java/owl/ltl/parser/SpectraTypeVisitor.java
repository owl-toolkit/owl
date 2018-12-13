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
import java.util.Optional;
import java.util.Set;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.grammar.SPECTRAParser;
import owl.grammar.SPECTRAParser.AdditiveContext;
import owl.grammar.SPECTRAParser.ConstContext;
import owl.grammar.SPECTRAParser.MultiplicativeContext;
import owl.grammar.SPECTRAParser.PrimaryContext;
import owl.grammar.SPECTRAParser.ReferableContext;
import owl.grammar.SPECTRAParser.RelationalContext;
import owl.grammar.SPECTRAParser.RemainderContext;
import owl.grammar.SPECTRAParser.SpecialNextContext;
import owl.grammar.SPECTRAParserBaseVisitor;
import owl.ltl.parser.SpectraParser.HigherOrderExpression;
import owl.ltl.parser.SpectraParser.SpectraType;

final class SpectraTypeVisitor extends SPECTRAParserBaseVisitor<Optional<SpectraType>> {
  private final Map<String, HigherOrderExpression> variables;
  private final Set<String> typeConstants;

  SpectraTypeVisitor(Map<String, HigherOrderExpression> variables,
                     Set<String> typeConstants) {
    this.variables = variables;
    this.typeConstants = typeConstants;
  }

  @Override
  public Optional<SpectraType> visitRelational(RelationalContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    Optional<SpectraType> left = visit(ctx.left);
    Optional<SpectraType> right = visit(ctx.right);

    if (left.isEmpty() && right.isEmpty()) {
      throw new ParseCancellationException("Both left and right children have no type");
    } else if (left.isPresent() && right.isPresent() && !left.equals(right)) {
      throw new ParseCancellationException("Type inconsistency of left and right children");
    }

    return (left.isPresent()) ? left : right;
  }

  @Override
  public Optional<SpectraType> visitRemainder(RemainderContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    Optional<SpectraType> left = visit(ctx.left);
    Optional<SpectraType> right = visit(ctx.right);

    if (left.isEmpty() && right.isEmpty()) {
      throw new ParseCancellationException("Both left and right children have no type");
    } else if (left.isPresent() && right.isPresent() && !left.equals(right)) {
      throw new ParseCancellationException("Type inconsistency of left and right children");
    }

    return (left.isPresent()) ? left : right;
  }

  @Override
  public Optional<SpectraType> visitAdditive(AdditiveContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    Optional<SpectraType> left = visit(ctx.left);
    Optional<SpectraType> right = visit(ctx.right);

    if (left.isEmpty() && right.isEmpty()) {
      throw new ParseCancellationException("Both left and right children have no type");
    } else if (left.isPresent() && right.isPresent() && !left.equals(right)) {
      throw new ParseCancellationException("Type inconsistency of left and right children");
    }

    return (left.isPresent()) ? left : right;
  }

  @Override
  public Optional<SpectraType> visitMultiplicative(MultiplicativeContext ctx) {
    assert ctx.getChildCount() == 3;
    assert ctx.left != null && ctx.right != null;

    Optional<SpectraType> left = visit(ctx.left);
    Optional<SpectraType> right = visit(ctx.right);

    if (left.isEmpty() && right.isEmpty()) {
      throw new ParseCancellationException("Both left and right children have no type");
    } else if (left.isPresent() && right.isPresent() && !left.equals(right)) {
      throw new ParseCancellationException("Type inconsistency of left and right children");
    }

    return (left.isPresent()) ? left : right;
  }

  @Override
  public Optional<SpectraType> visitPrimary(PrimaryContext ctx) {
    return visit(ctx.getChild(0));
  }

  @Override
  public Optional<SpectraType> visitConst(ConstContext ctx) {
    assert ctx.getChildCount() == 1;
    return Optional.empty();
  }

  @Override
  public Optional<SpectraType> visitReferable(ReferableContext ctx) {
    if (variables.containsKey(ctx.pointer.getText())) {
      return Optional.of(variables.get(ctx.pointer.getText()).getType());
    } else if (typeConstants.contains(ctx.pointer.getText())) {
      return Optional.empty();
    } else {
      throw new ParseCancellationException("Unsupported cross reference as of yet");
    }
  }

  @Override
  public Optional<SpectraType> visitSpecialNext(SpecialNextContext ctx) {
    assert ctx.getChildCount() == 4;
    return visit(ctx.getChild(3));
  }

  @Override
  public Optional<SpectraType> visitPredPatt(SPECTRAParser.PredPattContext ctx) {
    //TODO:
    throw new ParseCancellationException("Predicate and patterns not implemented yet");
  }
}
