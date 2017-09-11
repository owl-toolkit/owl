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

package owl.translations.ltl2ldba.breakpoint;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.LOGGER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.FrequencyG;
import owl.ltl.GOperator;
import owl.ltl.Literal;
import owl.ltl.MOperator;
import owl.ltl.ROperator;
import owl.ltl.UOperator;
import owl.ltl.WOperator;
import owl.ltl.XOperator;
import owl.ltl.rewriter.RewriterFactory;
import owl.ltl.rewriter.RewriterFactory.RewriterEnum;
import owl.ltl.visitors.Collector;
import owl.ltl.visitors.Visitor;
import owl.translations.Optimisation;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.Jump;

public class GObligationsJumpManager extends AbstractJumpManager<GObligations> {
  private final ImmutableSet<GObligations> obligations;

  private GObligationsJumpManager(EquivalenceClassFactory factory,
    EnumSet<Optimisation> optimisations, ImmutableSet<GObligations> obligations) {
    super(ImmutableSet.copyOf(optimisations), factory);
    this.obligations = obligations;
    LOGGER.log(Level.FINE, () ->  "The automaton has the following jumps: " + obligations);
  }

  private static Stream<Set<GOperator>> createGSetStream(EquivalenceClass state) {
    return Sets.powerSet(Collector.collectTransformedGOperators(state.getSupport())).stream();
  }

  public static GObligationsJumpManager build(EquivalenceClass initialState,
    EnumSet<Optimisation> optimisations) {

    if (initialState.testSupport(Fragments::isCoSafety) || initialState
      .testSupport(Fragments::isSafety)) {
      return new GObligationsJumpManager(initialState.getFactory(), optimisations,
        ImmutableSet.of());
    }

    // Compute resulting GObligations. -> Same GObligations; Different Associated Sets; what to do?
    ImmutableSet<GObligations> jumps = createDisjunctionStream(initialState,
      GObligationsJumpManager::createGSetStream)
      .map(Gs -> GObligations.build(Gs, initialState.getFactory(), optimisations))
      .filter(Objects::nonNull)
      .collect(ImmutableSet.toImmutableSet());

    return new GObligationsJumpManager(initialState.getFactory(), optimisations, jumps);
  }

  private static boolean containsAllPropostions(Collection<? extends Formula> set1,
    Collection<Formula> set2) {
    return set1.parallelStream().allMatch(x -> set2.stream()
      .anyMatch(y -> y.anyMatch(z -> x.equals(Collector.transformToGOperator(z)))));
  }

  @Override
  protected Set<Jump<GObligations>> computeJumps(EquivalenceClass state) {
    EquivalenceClass state2 = optimisations.contains(Optimisation.EAGER_UNFOLD)
                              ? state.duplicate()
                              : state.unfold();
    Set<Formula> support = state2.getSupport();
    Set<GObligations> availableObligations = obligations
      .stream().filter(x -> containsAllPropostions(x.goperators, support))
      .collect(Collectors.toSet());
    state2.free();

    Set<Jump<GObligations>> jumps = new HashSet<>();

    for (GObligations obligation : availableObligations) {
      EquivalenceClass remainder = evaluate(state, obligation);

      if (remainder.isFalse()) {
        continue;
      }

      if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)
        && dependsOnExternalAtoms(remainder, obligation)) {
        continue;
      }

      jumps.add(buildJump(remainder, obligation));
    }

    return jumps;
  }

  private boolean dependsOnExternalAtoms(EquivalenceClass remainder, GObligations obligation) {
    BitSet externalAtoms = Collector.collectAtoms(remainder.getSupport());
    BitSet internalAtoms = new BitSet();
    obligation.forEach(x -> internalAtoms.or(Collector.collectAtoms(x.getSupport())));

    // Check if external atoms are non-empty and disjoint.
    if (!externalAtoms.isEmpty()) {
      externalAtoms.and(internalAtoms);

      if (externalAtoms.isEmpty()) {
        remainder.free();
        return true;
      }
    }

    return false;
  }

  private EquivalenceClass evaluate(EquivalenceClass clazz, GObligations keys) {
    Formula formula = clazz.getRepresentative();
    EvaluateVisitor evaluateVisitor = new EvaluateVisitor(keys.goperators, keys.getObligation());
    Formula subst = formula.accept(evaluateVisitor);
    Formula evaluated = RewriterFactory.apply(RewriterEnum.MODAL, subst);
    EquivalenceClass goal = factory.createEquivalenceClass(evaluated);
    evaluateVisitor.free();
    return goal;
  }

  static class EvaluateVisitor implements Visitor<Formula> {

    private final EquivalenceClass environment;
    private final EquivalenceClassFactory factory;

    EvaluateVisitor(Iterable<GOperator> gMonitors, EquivalenceClass label) {
      this.factory = label.getFactory();
      this.environment = label.and(factory.createEquivalenceClass(Iterables.concat(gMonitors,
        StreamSupport.stream(gMonitors.spliterator(), false).map(x -> x.operand)
          .collect(Collectors.toList()))));
    }

    private Formula defaultAction(Formula formula) {
      EquivalenceClass clazz = factory.createEquivalenceClass(formula);
      boolean isTrue = environment.implies(clazz);
      clazz.free();
      return isTrue ? BooleanConstant.TRUE : formula;
    }

    void free() {
      environment.free();
    }

    @Override
    public Formula visit(BooleanConstant booleanConstant) {
      return booleanConstant;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      Formula defaultAction = defaultAction(conjunction);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return Conjunction.create(conjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      Formula defaultAction = defaultAction(disjunction);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return Disjunction.create(disjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      Formula defaultAction = defaultAction(fOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return FOperator.create(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(GOperator gOperator) {
      return BooleanConstant.get(gOperator.operand.accept(this) == BooleanConstant.TRUE);
    }

    @Override
    public Formula visit(Literal literal) {
      return defaultAction(literal);
    }

    @Override
    public Formula visit(MOperator mOperator) {
      Formula defaultAction = defaultAction(mOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return MOperator.create(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(UOperator uOperator) {
      Formula defaultAction = defaultAction(uOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return UOperator.create(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (wOperator.left.accept(this) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return UOperator.create(wOperator.left, wOperator.right).accept(this);
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (rOperator.right.accept(this) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return MOperator.create(rOperator.left, rOperator.right).accept(this);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      Formula defaultAction = defaultAction(xOperator);

      if (defaultAction instanceof BooleanConstant) {
        return defaultAction;
      }

      return XOperator.create(xOperator.operand.accept(this));
    }
  }
}