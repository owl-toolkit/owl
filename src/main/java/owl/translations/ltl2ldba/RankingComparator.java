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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import java.io.Serializable;
import java.util.Comparator;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.DefaultIntVisitor;

/*
 * Note: this comparator imposes orderings that are inconsistent with equals.
 */
public class RankingComparator implements Comparator<GOperator>, Serializable {

  private static final LoadingCache<GOperator, Integer> LOADING_CACHE = CacheBuilder.newBuilder()
    .maximumSize(1000L).build(
      new CacheLoader<GOperator, Integer>() {
        @Override
        public Integer load(GOperator key) {
          return key.accept(VISITOR);
        }
      });

  private static final RankVisitor VISITOR = new RankVisitor();

  @Override
  public int compare(GOperator o1, GOperator o2) {
    return Integer.compare(LOADING_CACHE.getUnchecked(o1), LOADING_CACHE.getUnchecked(o2));
  }

  private static class RankVisitor extends DefaultIntVisitor {

    @Override
    protected int defaultAction(Formula formula) {
      return 0;
    }

    @Override
    public int visit(Conjunction conjunction) {
      return conjunction.children.stream().mapToInt(x -> x.accept(this)).max().orElse(0);
    }

    @Override
    public int visit(Disjunction disjunction) {
      return disjunction.children.stream().mapToInt(x -> x.accept(this)).max().orElse(0);
    }

    @Override
    public int visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }

    @Override
    public int visit(GOperator gOperator) {
      return gOperator.operand.accept(this) + 1;
    }

    @Override
    public int visit(MOperator mOperator) {
      return Math.max(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public int visit(ROperator rOperator) {
      return Math.max(rOperator.left.accept(this), rOperator.right.accept(this) + 1);
    }

    @Override
    public int visit(UOperator uOperator) {
      return Math.max(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public int visit(WOperator wOperator) {
      return Math.max(wOperator.left.accept(this) + 1, wOperator.right.accept(this));
    }

    @Override
    public int visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }
}
