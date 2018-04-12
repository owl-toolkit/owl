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

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.Formula;
import owl.ltl.PropositionalFormula;
import owl.ltl.visitors.DefaultVisitor;

public final class NormalForms {
  private static final ConjunctiveNormalFormVisitor CNF = new ConjunctiveNormalFormVisitor();
  private static final DisjunctiveNormalFormVisitor DNF = new DisjunctiveNormalFormVisitor();

  private NormalForms() {}

  public static Set<Set<Formula>> toCnf(Formula formula) {
    return formula.accept(CNF);
  }

  public static Formula toCnfFormula(Formula formula) {
    return formula.accept(CNF).stream()
      .map(Disjunction::of)
      .reduce(BooleanConstant.TRUE, Conjunction::of);
  }

  public static Set<Set<Formula>> toDnf(Formula formula) {
    return formula.accept(DNF);
  }

  public static Formula toDnfFormula(Formula formula) {
    return formula.accept(DNF).stream()
      .map(Conjunction::of)
      .reduce(BooleanConstant.FALSE, Disjunction::of);
  }

  private static final class ConjunctiveNormalFormVisitor
    extends DefaultVisitor<Set<Set<Formula>>> {

    private static void minimise(Set<Set<Formula>> cnf) {
      cnf.removeIf(x -> cnf.stream().anyMatch(y -> x.size() < y.size() && y.containsAll(x)));
    }

    @Override
    protected Set<Set<Formula>> defaultAction(Formula formula) {
      return Set.of(Set.of(formula));
    }

    @Override
    public Set<Set<Formula>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? Set.of() : Set.of(Set.of());
    }

    @Override
    public Set<Set<Formula>> visit(Conjunction conjunction) {
      Set<Set<Formula>> cnf = new HashSet<>();
      conjunction.children.forEach(x -> cnf.addAll(x.accept(this)));
      minimise(cnf);
      return cnf;
    }

    @Override
    public Set<Set<Formula>> visit(Disjunction disjunction) {
      if (disjunction.children.stream().noneMatch(PropositionalFormula.class::isInstance)) {
        return Set.of(disjunction.children);
      }

      Set<Set<Formula>> cnf = Set.of(Set.of());

      for (Formula child : disjunction.children) {
        Set<Set<Formula>> childCnf = child.accept(this);
        Set<Set<Formula>> newCnf = new HashSet<>(cnf.size() * childCnf.size());

        for (Set<Formula> clause1 : cnf) {
          for (Set<Formula> clause2 : childCnf) {
            newCnf.add(Sets.union(clause1, clause2).immutableCopy());
          }
        }

        minimise(newCnf);
        cnf = newCnf;
      }

      return cnf;
    }
  }

  private static final class DisjunctiveNormalFormVisitor
    extends DefaultVisitor<Set<Set<Formula>>> {

    private static void minimise(Set<Set<Formula>> dnf) {
      dnf.removeIf(x -> dnf.stream().anyMatch(y -> x.size() > y.size() && x.containsAll(y)));
    }

    @Override
    protected Set<Set<Formula>> defaultAction(Formula formula) {
      return Set.of(Set.of(formula));
    }

    @Override
    public Set<Set<Formula>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? Set.of(Set.of()) : Set.of();
    }

    @Override
    public Set<Set<Formula>> visit(Conjunction conjunction) {
      if (conjunction.children.stream().noneMatch(PropositionalFormula.class::isInstance)) {
        return Set.of(conjunction.children);
      }

      Set<Set<Formula>> dnf = Set.of(Set.of());

      for (Formula child : conjunction.children) {
        Set<Set<Formula>> childDnf = child.accept(this);
        Set<Set<Formula>> newDnf = new HashSet<>(dnf.size() * childDnf.size());

        for (Set<Formula> clause1 : dnf) {
          for (Set<Formula> clause2 : childDnf) {
            newDnf.add(Sets.union(clause1, clause2).immutableCopy());
          }
        }

        minimise(newDnf);
        dnf = newDnf;
      }

      return dnf;
    }

    @Override
    public Set<Set<Formula>> visit(Disjunction disjunction) {
      Set<Set<Formula>> dnf = new HashSet<>();
      disjunction.children.forEach(x -> dnf.addAll(x.accept(this)));
      minimise(dnf);
      return dnf;
    }
  }
}
