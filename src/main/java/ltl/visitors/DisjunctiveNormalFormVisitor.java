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

package ltl.visitors;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import ltl.BooleanConstant;
import ltl.Conjunction;
import ltl.Disjunction;
import ltl.Formula;

public class DisjunctiveNormalFormVisitor extends DefaultVisitor<List<Set<Formula>>> {

  private static final DisjunctiveNormalFormVisitor INSTANCE = new DisjunctiveNormalFormVisitor();

  private DisjunctiveNormalFormVisitor() {

  }

  private static void minimise(List<Set<Formula>> dnf) {
    dnf.removeIf(set -> dnf.stream().anyMatch(subset -> set != subset && set.containsAll(subset)));
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
           Collections.singletonList(new HashSet<Formula>()) :
           Collections.emptyList();
  }

  @Override
  public List<Set<Formula>> visit(Conjunction conjunction) {
    List<Set<Formula>> dnf = new LinkedList<>();
    List<List<Set<Formula>>> allDnf = conjunction.children.stream().map(x -> x.accept(this))
      .collect(Collectors.toList());

    for (List<Set<Formula>> union : Lists.cartesianProduct(allDnf)) {
      dnf.add(union.stream().flatMap(Collection::stream).collect(Collectors.toSet()));
    }

    minimise(dnf);
    return dnf;
  }

  @Override
  public List<Set<Formula>> visit(Disjunction disjunction) {
    List<Set<Formula>> dnf = new LinkedList<>();
    disjunction.children.forEach(x -> dnf.addAll(x.accept(this)));
    minimise(dnf);
    return dnf;
  }
}
