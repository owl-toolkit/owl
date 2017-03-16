/*
 * Copyright (C) 2016  (See AUTHORS)
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

package owl.ltl.visitors;

import com.google.common.collect.Lists;
import java.util.List;
import javax.annotation.Nonnegative;
import owl.collections.Lists2;
import owl.collections.ValuationSet;
import owl.factories.ValuationSetFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;

public class HistoryValuationSetVisitor extends DefaultVisitor<List<ValuationSet>> {

  @Nonnegative
  private final int depth;
  private final ValuationSetFactory factory;

  public HistoryValuationSetVisitor(ValuationSetFactory factory, int depth) {
    this.factory = factory;
    this.depth = depth;
  }
  
  @Override
  protected List<ValuationSet> defaultAction(Formula formula) {
    throw new UnsupportedOperationException(formula.toString());
  }

  @Override
  public List<ValuationSet> visit(BooleanConstant booleanConstant) {
    return createHistory(booleanConstant.value);
  }

  @Override
  public List<ValuationSet> visit(Conjunction conjunction) {
    return conjunction.map(x -> x.accept(this))
      .reduce(HistoryValuationSetVisitor::intersection).orElseGet(() -> createHistory(true));
  }

  @Override
  public List<ValuationSet> visit(Disjunction disjunction) {
    return disjunction.map(x -> x.accept(this))
      .reduce(HistoryValuationSetVisitor::union).orElseGet(() -> createHistory(false));
  }

  @Override
  public List<ValuationSet> visit(Literal literal) {
    return createHistory(literal);
  }

  @Override
  public List<ValuationSet> visit(XOperator xOperator) {
    return createHistory(xOperator);
  }

  private List<ValuationSet> createHistory(Formula formula) {
    Formula literal = formula;
    int position = 0;

    while (literal instanceof XOperator) {
      position++;
      literal = ((XOperator) literal).operand;
    }

    List<ValuationSet> history = Lists.newArrayListWithExpectedSize(depth);

    for (int i = 0; i < depth; i++) {
      if (i == position) {
        history.add(factory.createValuationSet((Literal) literal));
      } else {
        history.add(factory.createUniverseValuationSet());
      }
    }

    return history;
  }

  private List<ValuationSet> createHistory(boolean value) {
    List<ValuationSet> history = Lists.newArrayListWithExpectedSize(depth);

    for (int i = 0; i < depth; i++) {
      if (value) {
        history.add(factory.createUniverseValuationSet());
      } else {
        history.add(factory.createEmptyValuationSet());
      }
    }

    return history;
  }

  private static List<ValuationSet> intersection(List<ValuationSet> history1,
    List<ValuationSet> history2) {
    Lists2.zip(history1, history2, ValuationSet::intersection);
    return history1;
  }

  private static List<ValuationSet> union(List<ValuationSet> history1,
    List<ValuationSet> history2) {
    Lists2.zip(history1, history2, ValuationSet::union);
    return history1;
  }
}
