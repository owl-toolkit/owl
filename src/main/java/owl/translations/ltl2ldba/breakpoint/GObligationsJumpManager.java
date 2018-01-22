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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import owl.collections.Collections3;
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
import owl.ltl.visitors.DefaultConverter;
import owl.translations.ltl2ldba.AbstractJumpManager;
import owl.translations.ltl2ldba.Jump;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public final class GObligationsJumpManager extends AbstractJumpManager<GObligations> {
  private static final Logger logger = Logger.getLogger(GObligationsJumpManager.class.getName());
  private final ImmutableSet<GObligations> obligations;

  private GObligationsJumpManager(EquivalenceClassFactory factory,
    ImmutableSet<Configuration> optimisations, ImmutableSet<GObligations> obligations) {
    super(optimisations, factory);
    this.obligations = obligations;
    logger.log(Level.FINE, () -> "The automaton has the following jumps: " + obligations);
  }

  public static GObligationsJumpManager build(EquivalenceClass initialState,
    ImmutableSet<Configuration> optimisations) {

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

  private static boolean containsAllPropositions(Collection<? extends Formula> set1,
    Collection<? extends Formula> set2) {
    BitSet obligationAtoms = Collector.collectAtoms(set1);
    BitSet supportAtoms = Collector.collectAtoms(set2);
    return Collections3.isSubsetConsuming(obligationAtoms, supportAtoms);
  }

  private static Stream<Set<GOperator>> createGSetStream(EquivalenceClass state) {
    return Sets.powerSet(Collector.collectTransformedGOperators(state.getSupport())).stream();
  }

  private static boolean dependsOnExternalAtoms(EquivalenceClass remainder,
    GObligations obligation) {
    BitSet externalAtoms = Collector.collectAtoms(remainder.getSupport());
    BitSet internalAtoms = new BitSet();
    obligation.forEach(x -> internalAtoms.or(Collector.collectAtoms(x.getSupport())));

    // Check if external atoms are non-empty and disjoint.
    if (!externalAtoms.isEmpty()) {
      externalAtoms.and(internalAtoms);
      return externalAtoms.isEmpty();
    }

    return false;
  }

  private static Formula evaluate(Formula formula, GObligations keys) {
    EvaluateVisitor evaluateVisitor =
      new EvaluateVisitor(keys.gOperators, keys.getObligation());
    Formula subst = formula.accept(evaluateVisitor);
    Formula evaluated = RewriterFactory.apply(RewriterEnum.MODAL, subst);
    evaluateVisitor.free();
    return evaluated;
  }

  @Override
  protected Set<Jump<GObligations>> computeJumps(EquivalenceClass state) {
    EquivalenceClass state2 = configuration.contains(Configuration.EAGER_UNFOLD)
      ? state.duplicate()
      : state.unfold();
    Set<Formula> support = state2.getSupport();
    Set<GObligations> availableObligations = new HashSet<>();

    for (GObligations x : obligations) {
      if (containsAllPropositions(x.gOperators, support)) {
        availableObligations.add(x);
      }
    }
    state2.free();

    Set<Jump<GObligations>> jumps = new HashSet<>();

    for (GObligations obligation : availableObligations) {
      EquivalenceClass remainder = evaluate(state, obligation);

      if (remainder.isFalse()) {
        continue;
      }

      if (configuration.contains(Configuration.SUPPRESS_JUMPS)
        && dependsOnExternalAtoms(remainder, obligation)) {
        remainder.free();
        continue;
      }

      jumps.add(buildJump(remainder, obligation));
    }

    return jumps;
  }

  private EquivalenceClass evaluate(EquivalenceClass clazz, GObligations keys) {
    Formula formula = clazz.getRepresentative();

    if (formula != null) {
      return factory.createEquivalenceClass(evaluate(formula, keys));
    }

    return clazz.substitute(x -> evaluate(x, keys));
  }

  static final class EvaluateVisitor extends DefaultConverter {
    private final EquivalenceClass environment;
    private final EquivalenceClassFactory factory;

    EvaluateVisitor(Collection<GOperator> gMonitors, EquivalenceClass label) {
      this.factory = label.getFactory();
      this.environment = label.and(factory.createEquivalenceClass(
        Stream.concat(gMonitors.stream(), gMonitors.stream().map(x -> x.operand))));
    }

    void free() {
      environment.free();
    }

    private boolean isImplied(Formula formula) {
      EquivalenceClass clazz = factory.createEquivalenceClass(formula);
      boolean isTrue = environment.implies(clazz);
      clazz.free();
      return isTrue;
    }

    @Override
    public Formula visit(Conjunction conjunction) {
      // Implication check not necessary for conjunctions.
      return Conjunction.of(conjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(Disjunction disjunction) {
      if (isImplied(disjunction)) {
        return BooleanConstant.TRUE;
      }

      return Disjunction.of(disjunction.children.stream().map(e -> e.accept(this)));
    }

    @Override
    public Formula visit(FOperator fOperator) {
      if (isImplied(fOperator)) {
        return BooleanConstant.TRUE;
      }

      return FOperator.of(fOperator.operand.accept(this));
    }

    @Override
    public Formula visit(FrequencyG freq) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Formula visit(GOperator gOperator) {
      if (isImplied(gOperator)) {
        return BooleanConstant.TRUE;
      }

      return BooleanConstant.of(gOperator.operand.accept(this) == BooleanConstant.TRUE);
    }

    @Override
    public Formula visit(Literal literal) {
      return isImplied(literal) ? BooleanConstant.TRUE : literal;
    }

    @Override
    public Formula visit(MOperator mOperator) {
      if (isImplied(mOperator)) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(mOperator.left.accept(this), mOperator.right.accept(this));
    }

    @Override
    public Formula visit(ROperator rOperator) {
      if (isImplied(rOperator)) {
        return BooleanConstant.TRUE;
      }

      if (rOperator.right.accept(this) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return MOperator.of(rOperator.left, rOperator.right).accept(this);
    }

    @Override
    public Formula visit(UOperator uOperator) {
      if (isImplied(uOperator)) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(uOperator.left.accept(this), uOperator.right.accept(this));
    }

    @Override
    public Formula visit(WOperator wOperator) {
      if (isImplied(wOperator)) {
        return BooleanConstant.TRUE;
      }

      if (wOperator.left.accept(this) == BooleanConstant.TRUE) {
        return BooleanConstant.TRUE;
      }

      return UOperator.of(wOperator.left, wOperator.right).accept(this);
    }

    @Override
    public Formula visit(XOperator xOperator) {
      if (isImplied(xOperator)) {
        return BooleanConstant.TRUE;
      }

      return XOperator.of(xOperator.operand.accept(this));
    }
  }
}