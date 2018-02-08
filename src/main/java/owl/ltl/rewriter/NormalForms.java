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

package owl.ltl.rewriter;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import owl.collections.Collections3;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.visitors.DefaultVisitor;

public final class NormalForms {
  private static final ConjunctiveNormalFormVisitor CNF = new ConjunctiveNormalFormVisitor();
  private static final DisjunctiveNormalFormVisitor DNF = new DisjunctiveNormalFormVisitor();

  private NormalForms() {}

  public static List<Set<Formula>> toCnf(Formula formula) {
    return formula.accept(CNF);
  }

  public static List<Set<Formula>> toDnf(Formula formula) {
    return formula.accept(DNF);
  }

  private static final class ConjunctiveNormalFormVisitor
    extends DefaultVisitor<List<Set<Formula>>> {
    ConjunctiveNormalFormVisitor() {
      // Empty.
    }

    private static void minimise(List<Set<Formula>> cnf) {
      cnf.removeIf(x -> cnf.stream().anyMatch(y -> x != y && y.containsAll(x)));
    }

    @Override
    protected List<Set<Formula>> defaultAction(Formula formula) {
      return List.of(Sets.newHashSet(formula));
    }

    @Override
    public List<Set<Formula>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? List.of() : List.of(new HashSet<>());
    }

    @Override
    public List<Set<Formula>> visit(Conjunction conjunction) {
      List<Set<Formula>> cnf = new ArrayList<>();
      conjunction.children.forEach(x -> cnf.addAll(x.accept(this)));
      minimise(cnf);
      return cnf;
    }

    @Override
    public List<Set<Formula>> visit(Disjunction disjunction) {
      List<Set<Formula>> cnf = new ArrayList<>();
      List<List<Set<Formula>>> allCnf = disjunction.children.stream().map(x -> x.accept(this))
        .collect(Collectors.toList());

      for (List<Set<Formula>> union : Lists.cartesianProduct(allCnf)) {
        cnf.add(Collections3.parallelUnion(union));
      }

      minimise(cnf);
      return cnf;
    }
  }

  private static final class DisjunctiveNormalFormVisitor
    extends DefaultVisitor<List<Set<Formula>>> {
    DisjunctiveNormalFormVisitor() {
      // Empty.
    }

    private static void minimise(List<Set<Formula>> dnf) {
      dnf.removeIf(x -> dnf.stream().anyMatch(y -> x != y && x.containsAll(y)));
    }

    @Override
    protected List<Set<Formula>> defaultAction(Formula formula) {
      return List.of(Sets.newHashSet(formula));
    }

    @Override
    public List<Set<Formula>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? List.of(new HashSet<>()) : List.of();
    }

    @Override
    public List<Set<Formula>> visit(Conjunction conjunction) {
      List<Set<Formula>> dnf = new ArrayList<>();
      List<List<Set<Formula>>> allDnf = conjunction.children.stream().map(x -> x.accept(this))
        .collect(Collectors.toList());

      for (List<Set<Formula>> union : Lists.cartesianProduct(allDnf)) {
        dnf.add(Collections3.parallelUnion(union));
      }

      minimise(dnf);
      return dnf;
    }

    @Override
    public List<Set<Formula>> visit(Disjunction disjunction) {
      List<Set<Formula>> dnf = new ArrayList<>();
      disjunction.children.forEach(x -> dnf.addAll(x.accept(this)));
      minimise(dnf);
      return dnf;
    }
  }
}
