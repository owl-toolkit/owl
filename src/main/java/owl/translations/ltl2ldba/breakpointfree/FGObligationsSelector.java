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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import owl.collections.Lists2;
import owl.collections.Sets2;
import owl.factories.EquivalenceClassFactory;
import owl.factories.EquivalenceClassUtil;
import owl.factories.Factories;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.UnaryModalOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.DefaultVisitor;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.EquivalenceClassStateFactory;
import owl.translations.ltl2ldba.JumpEvaluator;
import owl.translations.ltl2ldba.JumpSelector;

public final class FGObligationsSelector implements JumpSelector<FGObligations> {

  private final Table<ImmutableSet<FOperator>, ImmutableSet<GOperator>, FGObligations> cache;
  private final JumpEvaluator<FGObligations> evaluator;
  private final EquivalenceClassFactory factory;
  private final EnumSet<Optimisation> optimisations;
  private final EquivalenceClassStateFactory stateFactory;

  public FGObligationsSelector(Factories factories, EnumSet<Optimisation> optimisations) {
    this.optimisations = EnumSet.copyOf(optimisations);
    this.factory = factories.equivalenceClassFactory;
    evaluator = new FGObligationsEvaluator(factory);
    cache = HashBasedTable.create();
    stateFactory = new EquivalenceClassStateFactory(factory, optimisations);
  }

  private static <E> boolean isSubset(Collection<E> subset, Collection<E> set) {
    return set.containsAll(subset);
  }

  private static Multimap<Set<FOperator>, Set<GOperator>> selectAllMonitors(
    EquivalenceClass state) {
    Formula formula = state.getRepresentative();

    Set<GOperator> gOperators = Collector.collectTransformedGOperators(formula);
    Set<FOperator> fOperators = Collector.collectTransformedFOperators(gOperators);

    SetMultimap<Set<FOperator>, Set<GOperator>> multimap = MultimapBuilder
      .hashKeys()
      .hashSetValues()
      .build();

    for (Set<FOperator> fSet : Sets.powerSet(fOperators)) {
      for (Set<GOperator> gSet : Sets.powerSet(gOperators)) {
        multimap.put(fSet, gSet);
      }
    }

    return multimap;
  }

  private static Set<UnaryModalOperator> selectReducedMonitors(GOperator gOperator) {
    Set<FOperator> fOperators = Collector.collectTransformedFOperators(gOperator.operand, true);
    Set<UnaryModalOperator> operators = new HashSet<>();
    fOperators.forEach(x -> operators.addAll(selectReducedMonitors(x)));

    if (!(gOperator.operand instanceof FOperator)) {
      operators.add(gOperator);
    }

    return Sets.union(operators, fOperators);
  }

  private static Multimap<Set<FOperator>, Set<GOperator>> selectReducedMonitors(
    EquivalenceClass state) {
    SetMultimap<Set<FOperator>, Set<GOperator>> multimap = MultimapBuilder
      .hashKeys()
      .hashSetValues()
      .build();

    Formula formula = state.getRepresentative();
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
      multimap.put(ImmutableSet.of(), ImmutableSet.of());
    }

    return multimap;
  }

  private static Set<UnaryModalOperator> selectReducedMonitors(FOperator fOperator) {
    Set<GOperator> gOperators = Collector.collectTransformedGOperators(fOperator.operand, true);
    Set<UnaryModalOperator> operators = new HashSet<>();
    gOperators.forEach(x -> operators.addAll(selectReducedMonitors(x)));

    if (!(fOperator.operand instanceof GOperator)) {
      operators.add(fOperator);
    }

    return Sets.union(operators, gOperators);
  }

  private boolean containsSuperLanguage(@Nullable FGObligations set, Iterable<FGObligations> sets,
    EquivalenceClass master) {
    if (set == null) {
      return false;
    }

    for (FGObligations superSet : sets) {
      if (superSet == null || set.equals(superSet)) {
        continue;
      }

      if (isLanguageContained(set, superSet, master)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Determines if the first language is a subset of the second language.
   *
   * @param subset
   *     first language
   * @param set
   *     second language
   * @param master
   *     remainder
   *
   * @return true if is a sub-language
   */
  private boolean isLanguageContained(FGObligations subset, FGObligations set,
    EquivalenceClass master) {
    if (!isSubset(set.foperators, subset.foperators)) {
      return false;
    }

    EquivalenceClass subsetSafety = evaluator.evaluate(master, subset)
      .andWith(subset.getObligation());
    EquivalenceClass setSafety = evaluator.evaluate(master, set)
      .andWith(set.getObligation());

    boolean isLanguageContained = subsetSafety.implies(setSafety);

    if (isLanguageContained) {
      Logger.getGlobal().log(Level.FINER, () -> subset + " is included in  " + set);
      Logger.getGlobal().log(Level.FINER, () -> subsetSafety + " is included in  " + setSafety);
    }

    EquivalenceClassUtil.free(setSafety, subsetSafety);
    return isLanguageContained;
  }

  /* Literals are differently encoded in support */
  private static boolean equalsInSupport(Formula formula1, Formula formula2) {
    return formula1.equals(formula2) || formula1 instanceof Literal && formula2 instanceof Literal
      && ((Literal) formula1).getAtom() == ((Literal) formula2).getAtom();
  }

  @Override
  public Set<FGObligations> select(EquivalenceClass state, boolean isInitialState) {
    if (state.testSupport(Fragments::isCoSafety) || state.testSupport(Fragments::isSafety)) {
      Logger.getGlobal().log(Level.FINER, () -> state + " is finite/(co)safety. Suppressing jump.");
      return Collections.singleton(null);
    }

    assert !state.isFalse() : "False state should be filtered.";

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {


      // Check if there is a Satassigment of Safety properties. If yes suppress jump.
      /*EquivalenceClass safety = state.exists(Fragments::isSafety);
      boolean existsSafetyCore = safety.isTrue();
      safety.free();

      if (existsSafetyCore) {
        Logger.getGlobal().log(Level.FINER, () -> state + " has a safety core. Suppressing jumps.");
        return Collections.singleton(null);
      }*/



      // Check for dependency on co-safety properties.

      Set<Formula> notCoSafety = state.getSupport(x -> !Fragments.isCoSafety(x));
      EquivalenceClass coSafety = state.exists(x -> notCoSafety.stream()
        .anyMatch(y -> y.anyMatch(z -> equalsInSupport(x, z))));
      boolean existsExternalCondition = !coSafety.isTrue();
      coSafety.free();

      if (existsExternalCondition) {
        Logger.getGlobal().log(Level.FINER,
          () -> state + " has independent co-safety property. Suppressing jumps. Support"
          + notCoSafety + " CosafetySupport" + coSafety.getSupport());
        return Collections.singleton(null);
      }
    }

    // Compute resulting FGObligations.
    Multimap<Set<FOperator>, Set<GOperator>> keys;

    Set<FGObligations> fgObligations = new HashSet<>();

    // Find interesting Fs and Gs
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      keys = selectReducedMonitors(state);
    } else {
      keys = selectAllMonitors(state);
    }

    Logger.getGlobal().log(Level.FINER,
      () -> state + " can jump to the following components: " + keys);

    for (Map.Entry<Set<FOperator>, Set<GOperator>> entry : keys.entries()) {
      ImmutableSet<FOperator> fOperators = ImmutableSet.copyOf(entry.getKey());
      ImmutableSet<GOperator> gOperators = ImmutableSet.copyOf(entry.getValue());

      if (fOperators.isEmpty() && gOperators.isEmpty()) {
        fgObligations.add(null);
        continue;
      }

      // TODO: Bump to computeIfAbsent if available.
      FGObligations obligations = cache.get(fOperators, gOperators);

      if (obligations == null) {
        obligations = FGObligations.constructRecurringObligations(fOperators, gOperators, factory);

        if (obligations != null) {
          cache.put(fOperators, gOperators, obligations);
        }
      }

      if (obligations == null) {
        Logger.getGlobal().log(Level.FINER, () -> "Did not create FGObligations for " + entry);
        continue;
      }

      EquivalenceClass remainder = evaluator.evaluate(state, obligations);

      if (!remainder.isFalse()) {
        fgObligations.add(obligations);
      }
    }

    Logger.getGlobal().log(Level.FINER, () -> state + " has the following jumps: " + fgObligations);

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      fgObligations.removeIf(set -> containsSuperLanguage(set, fgObligations, state));
    }

    boolean patientState = true;

    if (fgObligations.size() == 1 && optimisations.contains(Optimisation.FORCE_JUMPS)) {
      Set<Formula> infinitelyOften = state.getSupport(GOperator.class::isInstance);

      if (state.equals(stateFactory.getInitial(Conjunction.create(infinitelyOften)))) {
        patientState = false;
      }
    }

    if (patientState) {
      fgObligations.add(null);
    }

    return fgObligations;
  }

  @VisibleForTesting
  abstract static class AbstractSelectVisitor
    extends DefaultVisitor<List<Set<UnaryModalOperator>>> {

    protected static <T> List<Set<T>> and(List<Set<T>> conjunct1, List<Set<T>> conjunct2) {
      return and(ImmutableList.of(conjunct1, conjunct2));
    }

    private static <T> List<Set<T>> and(Iterable<List<Set<T>>> conjuncts) {
      List<Set<T>> intersection = new ArrayList<>();

      for (List<Set<T>> sets : Lists.cartesianProduct(ImmutableList.copyOf(conjuncts))) {
        Lists2.addDistinct(intersection, Sets2.parallelUnion(sets));
      }

      return intersection;
    }

    protected static <T> List<Set<T>> or(Iterable<List<Set<T>>> disjuncts, boolean upwardClosure) {
      List<Set<T>> union = new ArrayList<>();

      if (!upwardClosure) {
        disjuncts.forEach(x -> Lists2.addAllDistinct(union, x));
        return union;
      }

      for (List<Set<T>> sets : Lists.cartesianProduct(ImmutableList.copyOf(disjuncts))) {
        for (Set<T> activeSet : sets) {
          Set<T> otherSetsUnion = sets.stream()
            .filter(x -> activeSet != x)
            .flatMap(Collection::stream)
            .collect(ImmutableSet.toImmutableSet());

          for (Set<T> x : Sets.powerSet(otherSetsUnion)) {
            Set<T> upwardClosedSet = Sets.newHashSet(Iterables.concat(activeSet, x));
            Lists2.addDistinct(union, upwardClosedSet);
          }
        }
      }

      return union;
    }

    protected static <T> List<Set<T>> or(List<Set<T>> disjunct1, List<Set<T>> disjunct2) {
      return or(ImmutableList.of(disjunct1, disjunct2), true);
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(BooleanConstant booleanConstant) {
      return booleanConstant.value
             ? Collections.singletonList(new HashSet<>())
             : Collections.emptyList();
    }

    @Override
    public List<Set<UnaryModalOperator>> visit(Literal literal) {
      return Collections.singletonList(new HashSet<>());
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
}
