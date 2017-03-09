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

package owl.translations.ltl2ldba;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultIntVisitor;
import owl.ltl.visitors.XDepthVisitor;

@SuppressFBWarnings("SE_COMPARATOR_SHOULD_BE_SERIALIZABLE")
public class RankingComparator implements Comparator<GOperator> {

  private final Map<GOperator, Integer> ranking = new HashMap<>();
  private final RankVisitor rankingVisitor = new RankVisitor();

  private static int max(int a, int b, int c) {
    return a > b ? (a > c ? a : c) : (b > c ? b : c);
  }

  @Override
  public int compare(GOperator o1, GOperator o2) {
    int rank1 = ranking.computeIfAbsent(o1, key -> {
      key.accept(rankingVisitor);
      return ranking.get(key);
    });
    int rank2 = ranking.computeIfAbsent(o2, key -> {
      key.accept(rankingVisitor);
      return ranking.get(key);
    });

    if (rank1 >= 0) {
      return rank2 >= 0 ? Integer.compare(rank1, rank2) : 1;
    }

    return rank2 < 0 ? Integer.compare(-rank1, -rank2) : -1;
  }

  private void insert(GOperator operator, int rank) {
    ranking.computeIfAbsent(operator,
      key -> rank < 0 ? -(XDepthVisitor.getDepth(operator.operand) + 1) : rank);
  }

  private class RankVisitor extends DefaultIntVisitor {

    @Override
    protected int defaultAction(Formula formula) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int visit(BooleanConstant booleanConstant) {
      return -1;
    }

    @Override
    public int visit(Conjunction conjunction) {
      return conjunction.children.stream().mapToInt(x -> x.accept(this)).max().orElse(-1);
    }

    @Override
    public int visit(Disjunction disjunction) {
      return disjunction.children.stream().mapToInt(x -> x.accept(this)).max().orElse(-1);
    }

    @Override
    public int visit(FOperator fOperator) {
      return Math.max(0, fOperator.operand.accept(this));
    }

    @Override
    public int visit(GOperator gOperator) {
      int depth = gOperator.operand.accept(this);
      insert(gOperator, depth);
      return Math.max(1, depth + 1);
    }

    @Override
    public int visit(Literal literal) {
      return -1;
    }

    @Override
    public int visit(MOperator mOperator) {
      return max(0, mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public int visit(ROperator rOperator) {
      int depth = rOperator.right.accept(this);
      insert(new GOperator(rOperator.right), depth);
      return max(1, depth + 1, rOperator.left.accept(this));
    }

    @Override
    public int visit(UOperator uOperator) {
      return max(0, uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public int visit(WOperator wOperator) {
      int depth = wOperator.left.accept(this);
      insert(new GOperator(wOperator.left), depth);
      return max(1, depth + 1, wOperator.right.accept(this));
    }

    @Override
    public int visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }
}
