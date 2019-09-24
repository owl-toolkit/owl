/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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

package owl.translations.mastertheorem;

import static java.util.stream.Collectors.toSet;

import com.google.common.collect.Sets;
import de.tum.in.naturals.bitset.BitSets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.collections.UpwardClosedSet;
import owl.ltl.BinaryModalOperator;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.SyntacticFragments;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.NormalForms;
import owl.ltl.visitors.BinaryVisitor;
import owl.ltl.visitors.Visitor;

public final class Selector {

  private Selector() {}

  public static Set<Fixpoints> selectAsymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectGreatestFixpoints(formula))
        .stream()
        .map(x -> Fixpoints.of(Set.of(), x))
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectAsymmetricFromClause)
        .collect(toSet());
    }
  }

  public static Set<Fixpoints> selectSymmetric(Formula formula, boolean all) {
    if (all) {
      return Sets.powerSet(selectAllFixpoints(formula))
        .stream()
        .map(Fixpoints::of)
        .collect(toSet());
    } else {
      return NormalForms
        .toDnf(formula, NormalForms.SYNTHETIC_CO_SAFETY_LITERAL)
        .stream()
        .flatMap(Selector::selectSymmetricFromClause)
        .collect(toSet());
    }
  }

  private static Stream<Fixpoints> selectAsymmetricFromClause(Set<Formula> clause) {
    List<Set<Set<Formula.ModalOperator>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      var fixpoints = selectGreatestFixpoints(element);

      if (!fixpoints.isEmpty()) {
        elementSets.add(Sets.powerSet(fixpoints));
      }
    }

    List<Fixpoints> fixpointsList = new ArrayList<>();

    for (List<Set<Formula.ModalOperator>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Formula.ModalOperator> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(Set.of(), union));
    }

    return fixpointsList.stream();
  }

  private static Stream<Fixpoints> selectSymmetricFromClause(Set<Formula> clause) {
    List<Fixpoints> fixpointsList = new ArrayList<>();
    List<Set<Set<Formula.ModalOperator>>> elementSets = new ArrayList<>();

    for (Formula element : clause) {
      assert isClauseElement(element);

      if (SyntacticFragments.isCoSafety(element)) {
        continue;
      }

      LinkedHashMap<Formula.ModalOperator, Integer> literalMapping = new LinkedHashMap<>();
      Set<Set<Formula.ModalOperator>> fixpoints = new HashSet<>();
      UnscopedVisitor visitor = new UnscopedVisitor(literalMapping);
      UpwardClosedSet set = element.accept(visitor);
      List<Formula.ModalOperator> mapping = List.copyOf(literalMapping.keySet());

      ScopeVisitor scopeVisitor = new ScopeVisitor();
      element.accept(scopeVisitor, null);

      outer:
      for (BitSet mask : BitSets.powerSet(literalMapping.size())) {
        if (set.contains(mask)) {
          var computedFixpoints = mask.stream().mapToObj(mapping::get).collect(toSet());

          for (Formula.ModalOperator fixpoint : computedFixpoints) {
            if (scopeVisitor.roots.contains(fixpoint)) {
              continue;
            }

            if (Predicates.IS_LEAST_FIXPOINT.test(fixpoint)) {
              var scopes = scopeVisitor.leastFixpointScopes.get(fixpoint);
              assert scopes != null : "Element should be marked as root.";

              if (Collections.disjoint(scopes, computedFixpoints)) {
                continue outer;
              }
            }

            if (Predicates.IS_GREATEST_FIXPOINT.test(fixpoint)) {
              var scopes = scopeVisitor.greatestFixpointScopes.get(fixpoint);
              assert scopes != null : "Element should be marked as root.";

              if (computedFixpoints.containsAll(scopes)) {
                continue outer;
              }
            }
          }

          fixpoints.add(computedFixpoints);
        }
      }

      elementSets.add(fixpoints);
    }

    for (List<Set<Formula.ModalOperator>> combination : Sets.cartesianProduct(elementSets)) {
      Set<Formula.ModalOperator> union = new HashSet<>();
      combination.forEach(union::addAll);
      fixpointsList.add(Fixpoints.of(union));
    }

    return fixpointsList.stream();
  }

  private static boolean isClauseElement(Formula formula) {
    return SyntacticFragments.isCoSafety(formula)
      || formula instanceof Literal
      || formula instanceof UnaryModalOperator
      || formula instanceof BinaryModalOperator;
  }

  private static Set<Formula.ModalOperator> selectAllFixpoints(
    Formula formula) {
    return formula.subformulas(Predicates.IS_FIXPOINT,
      Formula.ModalOperator.class::cast);
  }

  private static Set<Formula.ModalOperator> selectGreatestFixpoints(
    Formula formula) {
    return formula.subformulas(Predicates.IS_GREATEST_FIXPOINT,
      Formula.ModalOperator.class::cast);
  }

  private abstract static class AbstractSymmetricVisitor implements Visitor<UpwardClosedSet> {

    @Override
    public UpwardClosedSet visit(Conjunction conjunction) {
      UpwardClosedSet set = UpwardClosedSet.of(new BitSet());

      for (Formula x : conjunction.children) {
        set = set.intersection(x.accept(this));
      }

      return set;
    }

    @Override
    public UpwardClosedSet visit(Disjunction disjunction) {
      UpwardClosedSet set = UpwardClosedSet.of();

      for (Formula x : disjunction.children) {
        set = set.union(x.accept(this));
      }

      return set;
    }

    @Override
    public final UpwardClosedSet visit(Literal literal) {
      return UpwardClosedSet.of(new BitSet());
    }

    @Override
    public final UpwardClosedSet visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }
  }

  private static final class UnscopedVisitor extends AbstractSymmetricVisitor {
    private final GScopedVisitor gScopedVisitor;

    private UnscopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      gScopedVisitor = new GScopedVisitor(literals);
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      return gOperator.operand.accept(gScopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      if (SyntacticFragments.isCoSafety(mOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return mOperator.left.accept(this).intersection(mOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      if (SyntacticFragments.isSafety(rOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return rOperator.left.accept(this).union(rOperator.right.accept(gScopedVisitor));
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      if (SyntacticFragments.isCoSafety(uOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return uOperator.left.accept(this).union(uOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      if (SyntacticFragments.isSafety(wOperator)) {
        return UpwardClosedSet.of(new BitSet());
      }

      return wOperator.left.accept(gScopedVisitor).union(wOperator.right.accept(this));
    }
  }

  private static class GScopedVisitor extends AbstractSymmetricVisitor {
    private final ScopedVisitor scopedVisitor;

    private GScopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      this.scopedVisitor = new ScopedVisitor(literals);
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      return fOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      return gOperator.operand.accept(this);
    }

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      return mOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      return rOperator.left.accept(this).union(rOperator.right.accept(this));
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      return uOperator.accept(scopedVisitor);
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      return wOperator.left.accept(this).union(wOperator.right.accept(this));
    }
  }

  private static class ScopedVisitor extends AbstractSymmetricVisitor {
    private final Map<Formula.ModalOperator, Integer> literals;

    private ScopedVisitor(Map<Formula.ModalOperator, Integer> literals) {
      this.literals = literals;
    }

    @Override
    public UpwardClosedSet visit(FOperator fOperator) {
      // Register and terminate recursion.
      if (SyntacticFragments.isCoSafety(fOperator)) {
        return singleton(fOperator);
      }

      return visit((UnaryModalOperator) fOperator);
    }

    @Override
    public UpwardClosedSet visit(GOperator gOperator) {
      // Register and terminate recursion.
      if (SyntacticFragments.isSafety(gOperator.operand)) {
        return singleton(gOperator);
      }

      return visit((UnaryModalOperator) gOperator);
    }

    // Binary Modal Operators

    @Override
    public UpwardClosedSet visit(MOperator mOperator) {
      return visit((BinaryModalOperator) mOperator);
    }

    @Override
    public UpwardClosedSet visit(ROperator rOperator) {
      return visit((BinaryModalOperator) rOperator);
    }

    @Override
    public UpwardClosedSet visit(UOperator uOperator) {
      return visit((BinaryModalOperator) uOperator);
    }

    @Override
    public UpwardClosedSet visit(WOperator wOperator) {
      return visit((BinaryModalOperator) wOperator);
    }

    private UpwardClosedSet visit(UnaryModalOperator unaryModalOperator) {
      // We just explore for more literals, but actually we can't reason anymore...
      singleton(unaryModalOperator);
      unaryModalOperator.operand.accept(this);
      return UpwardClosedSet.of(new BitSet());
    }

    private UpwardClosedSet visit(BinaryModalOperator binaryModalOperator) {
      // We just explore for more literals, but actually we can't reason anymore...
      singleton(binaryModalOperator);
      binaryModalOperator.left.accept(this);
      binaryModalOperator.right.accept(this);
      return UpwardClosedSet.of(new BitSet());
    }

    protected UpwardClosedSet singleton(Formula.ModalOperator modalOperator) {
      BitSet bitSet = new BitSet();
      bitSet.set(literals.computeIfAbsent(modalOperator, x -> literals.size()));
      return UpwardClosedSet.of(bitSet);
    }
  }

  private static class ScopeVisitor implements BinaryVisitor<Formula.ModalOperator, Void> {
    private final Map<Formula.ModalOperator, Set<Formula.ModalOperator>> leastFixpointScopes
      = new HashMap<>();
    private final Map<Formula.ModalOperator, Set<Formula.ModalOperator>> greatestFixpointScopes
      = new HashMap<>();
    private final Set<Formula.ModalOperator> roots = new HashSet<>();

    @Override
    public Void visit(BooleanConstant booleanConstant, Formula.ModalOperator parameter) {
      return null;
    }

    @Override
    public Void visit(Conjunction conjunction, Formula.ModalOperator scope) {
      conjunction.children.forEach(x -> x.accept(this, scope));
      return null;
    }

    @Override
    public Void visit(Disjunction disjunction, Formula.ModalOperator scope) {
      disjunction.children.forEach(x -> x.accept(this, scope));
      return null;
    }

    @Override
    public Void visit(FOperator fOperator, Formula.ModalOperator scope) {
      visitLeastFixpoint(fOperator, scope);
      return null;
    }

    @Override
    public Void visit(GOperator gOperator, Formula.ModalOperator scope) {
      visitGreatestFixpoint(gOperator, scope);
      return null;
    }

    @Override
    public Void visit(Literal literal, Formula.ModalOperator scope) {
      return null;
    }

    @Override
    public Void visit(MOperator mOperator, Formula.ModalOperator scope) {
      visitLeastFixpoint(mOperator, scope);
      return null;
    }

    @Override
    public Void visit(UOperator uOperator, Formula.ModalOperator scope) {
      visitLeastFixpoint(uOperator, scope);
      return null;
    }

    @Override
    public Void visit(ROperator rOperator, Formula.ModalOperator scope) {
      visitGreatestFixpoint(rOperator, scope);
      return null;
    }

    @Override
    public Void visit(WOperator wOperator, Formula.ModalOperator scope) {
      visitGreatestFixpoint(wOperator, scope);
      return null;
    }

    @Override
    public Void visit(XOperator xOperator, Formula.ModalOperator scope) {
      xOperator.operand.accept(this, scope);
      return null;
    }

    private void visitLeastFixpoint(Formula.ModalOperator lfp,
      @Nullable Formula.ModalOperator scope) {
      assert Predicates.IS_LEAST_FIXPOINT.test(lfp);

      // F is replaced by either tt or ff. Thus we do not need to track anything.
      var nextScope = scope instanceof FOperator ? scope : lfp;
      lfp.children().forEach(x -> x.accept(this, nextScope));

      if (scope == null || Predicates.IS_GREATEST_FIXPOINT.test(scope)) {
        roots.add(lfp);
      } else {
        assert Predicates.IS_LEAST_FIXPOINT.test(scope);
        leastFixpointScopes.merge(lfp,
          scope instanceof FOperator ? Set.of() : Set.of(scope),
          Sets::union);
      }
    }

    private void visitGreatestFixpoint(Formula.ModalOperator gfp,
      @Nullable Formula.ModalOperator scope) {
      assert Predicates.IS_GREATEST_FIXPOINT.test(gfp);

      // G is replaced by either tt or ff. Thus we do not need to track anything.
      var nextScope = scope instanceof GOperator ? scope : gfp;
      gfp.children().forEach(x -> x.accept(this, nextScope));

      if (scope == null || Predicates.IS_LEAST_FIXPOINT.test(scope)) {
        roots.add(gfp);
      } else {
        assert Predicates.IS_GREATEST_FIXPOINT.test(scope);
        greatestFixpointScopes.merge(gfp,
          scope instanceof GOperator ? Set.of() : Set.of(scope),
          Sets::union);
      }
    }
  }
}
