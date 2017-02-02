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
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;

public class ConjunctiveNormalFormVisitor extends DefaultVisitor<List<Set<Formula>>> {

  private static final ConjunctiveNormalFormVisitor INSTANCE = new ConjunctiveNormalFormVisitor();

  private ConjunctiveNormalFormVisitor() {

  }

  private static void minimise(List<Set<Formula>> cnf) {
    cnf.removeIf(subset -> cnf.stream().anyMatch(set -> set != subset && subset.containsAll(set)));
  }

  public static List<Set<Formula>> normaliseStatic(Formula formula) {
    return formula.accept(INSTANCE);
  }

  @Override
  protected List<Set<Formula>> defaultAction(Formula formula) {
    return Collections.singletonList(Sets.newHashSet(formula));
  }

  @Override
  public List<Set<Formula>> visit(BooleanConstant booleanConstant) {
    return booleanConstant.value ?
           Collections.emptyList() :
           Collections.singletonList(new HashSet<Formula>());
  }

  @Override
  public List<Set<Formula>> visit(Conjunction conjunction) {
    List<Set<Formula>> cnf = new LinkedList<>();
    conjunction.children.forEach(x -> cnf.addAll(x.accept(this)));
    minimise(cnf);
    return cnf;
  }

  @Override
  public List<Set<Formula>> visit(Disjunction disjunction) {
    List<Set<Formula>> cnf = new LinkedList<>();
    List<List<Set<Formula>>> allCnf = disjunction.children.stream().map(x -> x.accept(this))
      .collect(Collectors.toList());

    for (List<Set<Formula>> union : Lists.cartesianProduct(allCnf)) {
      cnf.add(union.stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    minimise(cnf);
    return cnf;
  }
}
