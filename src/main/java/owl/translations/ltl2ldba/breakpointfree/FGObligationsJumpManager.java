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

package owl.translations.ltl2ldba.breakpointfree;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import owl.collections.Collections3;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.SimplifierFactory;
import owl.ltl.rewriter.SimplifierFactory.Mode;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.Jump;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class FGObligationsJumpManager extends AbstractJumpManager<FGObligations> {

  private final Table<Set<FOperator>, Set<GOperator>, FGObligations> cache;

  private FGObligationsJumpManager(EquivalenceClassFactory factories,
    Set<Configuration> optimisations, Set<Formula> modalOperators, Formula initialFormula) {
    super(optimisations, factories, modalOperators, initialFormula);
    cache = HashBasedTable.create();
  }

  public static FGObligationsJumpManager build(Formula formula, EquivalenceClassFactory factory,
    Set<Configuration> optimisations) {
    return new FGObligationsJumpManager(factory, optimisations,
      factory.of(formula).modalOperators(), formula);
  }

  private static Stream<Map.Entry<Set<FOperator>, Set<GOperator>>> createFGSetStream(
    Formula state) {
    Set<GOperator> gOperators = Collector.collectTransformedGOperators(state);
    Set<FOperator> fOperators = Collector.collectTransformedFOperators(gOperators);

    // Prefilter
    gOperators.removeIf(x -> x.operand instanceof FOperator);
    fOperators.removeIf(x -> x.operand instanceof GOperator);

    SetMultimap<Set<FOperator>, Set<GOperator>> multimap = MultimapBuilder
      .hashKeys()
      .hashSetValues()
      .build();

    for (Set<FOperator> fSet : Sets.powerSet(fOperators)) {
      for (Set<GOperator> gSet : Sets.powerSet(gOperators)) {
        multimap.put(fSet, gSet);
      }
    }

    return multimap.entries().stream();
  }

  // TODO: also use GOps Information
  static Formula replaceFOperators(Set<FOperator> trueFOperators,
    Set<GOperator> trueGOperators, GOperator formula) {
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(trueFOperators, trueGOperators);
    return GOperator.of(formula.operand.accept(visitor));
  }

  private static Formula replaceFOperators(FGObligations obligations, Formula formula) {
    ReplaceFOperatorsVisitor visitor = new ReplaceFOperatorsVisitor(obligations.fOperators,
      obligations.gOperators);
    return formula.accept(visitor);
  }

  static Formula replaceGOperators(Set<GOperator> trueGOperators,
    Set<FOperator> trueFOperators, Formula formula) {
    ReplaceGOperatorsVisitor visitor = new ReplaceGOperatorsVisitor(trueGOperators, trueFOperators);
    return formula.accept(visitor);
  }

  static Multimap<Set<FOperator>, Set<GOperator>> selectReducedMonitors(
    EquivalenceClass state) {
    SetMultimap<Set<FOperator>, Set<GOperator>> multimap = MultimapBuilder
      .hashKeys()
      .hashSetValues()
      .build();

    Formula formula = state.representative();
    boolean delayJump = false;

    for (Set<UnaryModalOperator> obligations : formula.accept(ToplevelSelectVisitor.INSTANCE)) {
      Set<FOperator> fOperators = obligations.stream()
        .filter(FOperator.class::isInstance)
        .map(FOperator.class::cast)
        .collect(Collectors.toSet());

      Set<GOperator> gOperators = obligations.stream()
        .filter(GOperator.class::isInstance)
        .map(GOperator.class::cast)
        .collect(Collectors.toSet());

      delayJump |= fOperators.removeIf(x -> x.operand instanceof GOperator);
      gOperators.removeIf(x -> x.operand instanceof FOperator);

      multimap.put(fOperators, gOperators);
    }

    if (delayJump) {
      multimap.put(Set.of(), Set.of());
    }

    return multimap;
  }

  @Override
  protected Set<Jump<FGObligations>> computeJumps(EquivalenceClass state) {
    Set<Jump<FGObligations>> fgObligations = new HashSet<>();

    createDisjunctionStream(state, FGObligationsJumpManager::createFGSetStream).forEach(entry -> {
      Set<FOperator> fOperators = Set.copyOf(entry.getKey());
      Set<GOperator> gOperators = Set.copyOf(entry.getValue());

      FGObligations obligations = cache.get(fOperators, gOperators);

      if (obligations == null) {
        obligations = FGObligations.build(fOperators, gOperators, factory);

        if (obligations != null) {
          cache.put(fOperators, gOperators, obligations);
        }
      }

      if (obligations == null) {
        Logger.getGlobal().log(Level.FINER, () -> "Did not create FGObligations for " + entry);
        return;
      }

      EquivalenceClass remainder = evaluate(state, obligations);

      if (!remainder.isFalse()) {
        fgObligations.add(buildJump(remainder, obligations));
      }
    });

    return fgObligations;
  }

  private EquivalenceClass evaluate(EquivalenceClass clazz, FGObligations obligation) {
    // TODO: use substitute
    Formula formula = clazz.representative();
    Formula fFreeFormula = replaceFOperators(obligation, formula);
    Formula evaluated = SimplifierFactory.apply(fFreeFormula, Mode.SYNTACTIC);
    Logger.getGlobal().log(Level.FINER, () -> "Rewrote " + clazz + " into " + evaluated
      + " using " + obligation);
    return factory.of(evaluated);
  }

  abstract static class AbstractReplaceOperatorsVisitor extends DefaultVisitor<Formula> {
    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      return Conjunction.of(conjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      return Disjunction.of(disjunction.map(x -> x.accept(this)));
    }

    @Override
    public Formula visit(Literal literal) {
      return literal;
    }

    @Override
    public Formula visit(XOperator xOperator) {
      return XOperator.of(xOperator.operand.accept(this));
    }
  }

  @VisibleForTesting
  abstract static class AbstractSelectVisitor
    extends DefaultVisitor<List<Set<UnaryModalOperator>>> {

    protected static <T> List<Set<T>> and(List<Set<T>> conjunct1, List<Set<T>> conjunct2) {
      return and(List.of(conjunct1, conjunct2));
    }

    private static <T> List<Set<T>> and(Collection<List<Set<T>>> conjuncts) {
      List<Set<T>> intersection = new ArrayList<>();

      for (List<Set<T>> sets : Lists.cartesianProduct(List.copyOf(conjuncts))) {
        Collections3.addDistinct(intersection, Collections3.parallelUnion(sets));
      }

      return intersection;
    }

    protected static <T> List<Set<T>> or(Collection<List<Set<T>>> disjuncts,
      boolean upwardClosure) {
      List<Set<T>> union = new ArrayList<>();

      if (!upwardClosure) {
        disjuncts.forEach(x -> Collections3.addAllDistinct(union, x));
        return union;
      }

      for (List<Set<T>> sets : Lists.cartesianProduct(List.copyOf(disjuncts))) {
        for (Set<T> activeSet : sets) {
          Set<T> otherSetsUnion = sets.stream()
            .filter(x -> activeSet != x)
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableSet());

          for (Set<T> x : Sets.powerSet(otherSetsUnion)) {
            Set<T> upwardClosedSet = Sets.newHashSet(Iterables.concat(activeSet, x));
            Collections3.addDistinct(union, upwardClosedSet);
          }
        }
      }

      return union;
    }

    protected static <T> List<Set<T>> or(List<Set<T>> disjunct1, List<Set<T>> disjunct2) {
      return or(List.of(disjunct1, disjunct2), true);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value ? List.of(new HashSet<>()) : List.of();
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(Literal literal) {
      return List.of(new HashSet<>());
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(XOperator xOperator) {
      return xOperator.operand.accept(this);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(Conjunction conjunction) {
      return and(Collections2.transform(conjunction.children, x -> x.accept(this)));
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(Disjunction disjunction) {
      return or(Collections2.transform(disjunction.children, x -> x.accept(this)), true);
    }
  }

  @VisibleForTesting
  static class FScopedSelectVisitor extends AbstractSelectVisitor {

    static final FScopedSelectVisitor INSTANCE = new FScopedSelectVisitor();

    @Override
    public List<Set<UnaryModalOperator>> visit(FOperator fOperator) {
      return fOperator.operand.accept(this);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(GOperator gOperator) {
      List<Set<UnaryModalOperator>> sets = gOperator.operand.accept(GScopedSelectVisitor.INSTANCE);
      sets.forEach(x -> x.add(gOperator));
      return sets;
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(MOperator mOperator) {
      return visitM(mOperator.left, mOperator.right);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(ROperator rOperator) {
      // Assume G(right)
      List<Set<UnaryModalOperator>> gSet = rOperator.right.accept(GScopedSelectVisitor.INSTANCE);

      // Create G for R
      GOperator gOperator = new GOperator(rOperator.right);
      gSet.forEach(x -> x.add(gOperator));

      // Fallback to M
      return or(gSet, visitM(rOperator.left, rOperator.right));
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(UOperator uOperator) {
      return visitU(uOperator.left, uOperator.right);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(WOperator wOperator) {
      // Assume G(left)
      List<Set<UnaryModalOperator>> gSet = wOperator.left.accept(GScopedSelectVisitor.INSTANCE);

      // Create G for W
      GOperator gOperator = new GOperator(wOperator.left);
      gSet.forEach(x -> x.add(gOperator));

      // Fallback to U
      return or(gSet, visitU(wOperator.left, wOperator.right));
    }

    private List<Set<UnaryModalOperator>> visitM(Formula left, Formula right) {
      List<Set<UnaryModalOperator>> leftSets = left.accept(this);
      List<Set<UnaryModalOperator>> rightSets = right.accept(this);
      return and(leftSets, rightSets);
    }

    private List<Set<UnaryModalOperator>> visitU(Formula left, Formula right) {
      List<Set<UnaryModalOperator>> leftSets = left.accept(this);
      List<Set<UnaryModalOperator>> rightSets = right.accept(this);
      return or(and(leftSets, rightSets), rightSets);
    }
  }

  @VisibleForTesting
  static class GScopedSelectVisitor extends AbstractSelectVisitor {

    static final GScopedSelectVisitor INSTANCE = new GScopedSelectVisitor();

    @Override
    public List<Set<UnaryModalOperator>> visit(FOperator fOperator) {
      List<Set<UnaryModalOperator>> sets = fOperator.operand.accept(FScopedSelectVisitor.INSTANCE);
      sets.forEach(x -> x.add(fOperator));
      return sets;
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(GOperator gOperator) {
      return gOperator.operand.accept(this);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(MOperator mOperator) {
      List<Set<UnaryModalOperator>> left = mOperator.left.accept(FScopedSelectVisitor.INSTANCE);
      List<Set<UnaryModalOperator>> right = mOperator.right.accept(this);

      // Create F from M.
      FOperator fOperator = new FOperator(mOperator.left);
      left.forEach(x -> x.add(fOperator));

      // Both sides need to hold for M.
      return and(left, right);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(ROperator rOperator) {
      return visitR(rOperator.left, rOperator.right);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(UOperator uOperator) {
      List<Set<UnaryModalOperator>> left = uOperator.left.accept(this);
      List<Set<UnaryModalOperator>> right = uOperator.right.accept(FScopedSelectVisitor.INSTANCE);

      // Create F from U.
      FOperator fOperator = new FOperator(uOperator.right);
      right.forEach(x -> x.add(fOperator));

      // Assume left does not hold or both sides hold;
      // TODO: extend left with empty list?
      return or(right, and(left, right));
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(WOperator wOperator) {
      return visitW(wOperator.left, wOperator.right);
    }

    private List<Set<UnaryModalOperator>> visitR(Formula left, Formula right) {
      List<Set<UnaryModalOperator>> leftSets = left.accept(this);
      List<Set<UnaryModalOperator>> rightSets = right.accept(this);
      return or(rightSets, and(leftSets, rightSets));
    }

    private List<Set<UnaryModalOperator>> visitW(Formula left, Formula right) {
      List<Set<UnaryModalOperator>> leftSets = left.accept(this);
      List<Set<UnaryModalOperator>> rightSets = right.accept(this);
      return or(leftSets, and(leftSets, rightSets));
    }
  }

  static class ReplaceFOperatorsVisitor extends AbstractReplaceOperatorsVisitor {
    private final Set<FOperator> foperators;
    private final Set<GOperator> goperators;

    ReplaceFOperatorsVisitor(Set<FOperator> foperators, Set<GOperator> goperators) {
      this.foperators = Set.copyOf(foperators);
      this.goperators = Set.copyOf(Sets.newHashSet(Iterables.concat(goperators,
        Collections2.transform(foperators, GOperator::new))));
    }

    private boolean isTrueFOperator(FOperator fOperator) {
      return ((fOperator.operand instanceof GOperator) && goperators.contains(fOperator.operand))
        || foperators.contains(fOperator);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return BooleanConstant.of(isTrueFOperator(fOperator));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (gOperator.operand instanceof FOperator && foperators.contains(gOperator.operand)) {
        return BooleanConstant.TRUE;
      }

      return BooleanConstant.of(goperators.contains(gOperator));
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (isTrueFOperator(new FOperator(mOperator.left))) {
        return ROperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isTrueFOperator(new FOperator(uOperator.right))) {
        return WOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
      }

      return BooleanConstant.FALSE;
    }

    @Override
    public Formula visit(ROperator rOperator) {
      return ROperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      return WOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }

  static class ReplaceGOperatorsVisitor extends AbstractReplaceOperatorsVisitor {
    private final Set<FOperator> foperators;
    private final Set<GOperator> goperators;

    ReplaceGOperatorsVisitor(Set<GOperator> goperators, Set<FOperator> foperators) {
      this.goperators = Set.copyOf(goperators);
      this.foperators = Set.copyOf(foperators);
    }

    private boolean isTrueGOperator(GOperator gOperator) {
      return (gOperator.operand instanceof FOperator && foperators.contains(gOperator.operand))
        || goperators.contains(gOperator);
    }

    @Override
    public Formula visit(FOperator fOperator) {
      return FOperator.of(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.of(isTrueGOperator(gOperator));
    }

    @Override
    public Formula visit(Literal literal) {
      // TODO: extend this?
      if (goperators.contains(new GOperator(literal))) {
        return BooleanConstant.TRUE;
      }

      return literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (isTrueGOperator(new GOperator(rOperator.right))) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(rOperator.left.accept(this), rOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (isTrueGOperator(new GOperator(wOperator.left))) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(wOperator.left.accept(this), wOperator.right.accept(this));
    }
  }

  @VisibleForTesting
  static class ToplevelSelectVisitor extends AbstractSelectVisitor {

    static final ToplevelSelectVisitor INSTANCE = new ToplevelSelectVisitor();

    @Override
    protected List<Set<UnaryModalOperator>> defaultAction(Formula formula) {
      return formula.accept(FScopedSelectVisitor.INSTANCE);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(Disjunction disjunction) {
      return or(Collections2.transform(disjunction.children, x -> x.accept(this)), false);
    }
  }
}
