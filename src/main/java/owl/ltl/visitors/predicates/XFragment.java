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

package owl.ltl.visitors.predicates;

import java.util.function.Predicate;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultIntVisitor;

public final class XFragment implements Predicate<Formula> {

  public static final XFragment INSTANCE = new XFragment();
  private static final PredicateVisitor VISITOR = new PredicateVisitor();

  private XFragment() {
  }

  public static boolean testStatic(Formula formula) {
    return INSTANCE.test(formula);
  }

  @Override
  public boolean test(Formula formula) {
    return formula.accept(VISITOR) == 1;
  }

  private static final class PredicateVisitor extends DefaultIntVisitor {
    @Override
    protected int defaultAction(Formula formula) {
      return 0;
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return 1;
    }

    @Override
    public int visit(Conjunction conjunction) {
      return conjunction.allMatch(c -> c.accept(this) == 1) ? 1 : 0;
    }

    @Override
    public int visit(Disjunction disjunction) {
      return disjunction.allMatch(c -> c.accept(this) == 1) ? 1 : 0;
    }

    @Override
    public int visit(Literal literal) {
      return 1;
    }

    @Override
    public int visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }
}
