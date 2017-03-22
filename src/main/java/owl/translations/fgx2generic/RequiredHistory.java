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

package owl.translations.fgx2generic;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import owl.collections.Lists2;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.Literal;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultVisitor;

class RequiredHistory {

  private static final Extractor INSTANCE = new Extractor();

  private RequiredHistory() {
  }

  static List<BitSet> getRequiredHistory(Formula formula) {
    List<BitSet> reversedRequiredHistory = formula.accept(INSTANCE);

    if (reversedRequiredHistory.isEmpty()) {
      return Collections.emptyList();
    }

    reversedRequiredHistory = reversedRequiredHistory
      .subList(0, reversedRequiredHistory.size() - 1);
    List<BitSet> requiredHistory = new ArrayList<>(Lists.reverse(reversedRequiredHistory));

    // Closure
    for (int i = requiredHistory.size() - 1; i > 0; i--) {
      BitSet set = requiredHistory.get(i);
      requiredHistory.subList(0, i).forEach(x -> x.or(set));
    }

    return requiredHistory;
  }

  static class Extractor extends DefaultVisitor<List<BitSet>> {
    @Override
    public List<BitSet> visit(BooleanConstant booleanConstant) {
      return Collections.emptyList();
    }

    @Override
    public List<BitSet> visit(Conjunction conjunction) {
      return conjunction.map(x -> x.accept(this)).reduce(new ArrayList<>(), Util::union);
    }

    @Override
    public List<BitSet> visit(Disjunction disjunction) {
      return disjunction.map(x -> x.accept(this)).reduce(new ArrayList<>(), Util::union);
    }

    @Override
    public List<BitSet> visit(Literal literal) {
      BitSet alphabet = new BitSet();
      alphabet.set(literal.getAtom());
      return Collections.singletonList(alphabet);
    }

    @Override
    public List<BitSet> visit(XOperator xOperator) {
      return Lists2.cons(new BitSet(), xOperator.operand.accept(this));
    }
  }
}
