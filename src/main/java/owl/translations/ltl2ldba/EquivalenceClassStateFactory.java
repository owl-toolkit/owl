/*
 * Copyright (C) 2016 - 2018  (See AUTHORS)
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

package owl.translations.ltl2ldba;

import static owl.collections.ValuationTree.cartesianProduct;

import com.google.common.base.Preconditions;
import java.util.BitSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import owl.automaton.edge.Edge;
import owl.collections.Collections3;
import owl.collections.ValuationTree;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.Conjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.FOperator;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.SyntacticFragments;
import owl.ltl.UnaryModalOperator;
import owl.ltl.rewriter.NormalForms;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;
import owl.util.annotation.Tuple;

public class EquivalenceClassStateFactory {

  private final boolean eagerUnfold;
  protected final EquivalenceClassFactory factory;
  private final boolean removeRedundantObligations;

  EquivalenceClassStateFactory(EquivalenceClassFactory factory, Set<Configuration> configuration) {
    this(factory, configuration.contains(Configuration.EAGER_UNFOLD),
      configuration.contains(Configuration.OPTIMISED_STATE_STRUCTURE));
  }

  EquivalenceClassStateFactory(EquivalenceClassFactory factory, boolean eagerUnfold,
    boolean removeRedundantObligations) {
    this.factory = factory;
    this.eagerUnfold = eagerUnfold;
    this.removeRedundantObligations = removeRedundantObligations;
  }

  EquivalenceClass getInitial(Formula formula) {
    return getInitial(factory.of(formula));
  }

  public EquivalenceClass getInitial(EquivalenceClass clazz, EquivalenceClass... environmentArray) {
    EquivalenceClass initial = eagerUnfold ? clazz.unfold() : clazz;
    return removeRedundantObligations(initial, environmentArray);
  }

  EquivalenceClass nondeterministicPreSuccessor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold ? clazz.temporalStep(valuation) : clazz.unfoldTemporalStep(valuation);
  }

  public BitSet sensitiveAlphabet(EquivalenceClass clazz) {
    if (eagerUnfold) {
      return clazz.atomicPropositions();
    } else {
      return clazz.unfold().atomicPropositions();
    }
  }

  ValuationTree<EquivalenceClass> successorTree(EquivalenceClass clazz) {
    return eagerUnfold
      ? clazz.temporalStepTree(preSuccessor -> Set.of(preSuccessor.unfold()))
      : clazz.unfold().temporalStepTree(Set::of);
  }

  ValuationTree<Edge<EquivalenceClass>> successorTree(EquivalenceClass clazz,
    Function<EquivalenceClass, Set<Edge<EquivalenceClass>>> edgeFunction) {
    return eagerUnfold
      ? clazz.temporalStepTree(x -> edgeFunction.apply(x.unfold()))
      : clazz.unfold().temporalStepTree(edgeFunction);
  }

  EquivalenceClass successor(EquivalenceClass clazz, BitSet valuation) {
    return eagerUnfold
      ? clazz.temporalStepUnfold(valuation)
      : clazz.unfoldTemporalStep(valuation);
  }

  public EquivalenceClass successor(EquivalenceClass clazz, BitSet valuation,
    EquivalenceClass... environmentArray) {
    return removeRedundantObligations(successor(clazz, valuation), environmentArray);
  }

  @Nullable
  public EquivalenceClass[] successors(EquivalenceClass[] clazz, BitSet valuation,
    @Nullable EquivalenceClass environment) {
    EquivalenceClass[] successors = new EquivalenceClass[clazz.length];

    for (int i = clazz.length - 1; i >= 0; i--) {
      successors[i] = successor(clazz[i], valuation, environment);

      if (successors[i].isFalse()) {
        return null;
      }
    }

    return successors;
  }

  private EquivalenceClass removeRedundantObligations(EquivalenceClass state,
    EquivalenceClass... environmentArray) {
    if (removeRedundantObligations && environmentArray.length > 0) {
      EquivalenceClass environment = factory.conjunction(environmentArray);

      if (environment.implies(state)) {
        return factory.getTrue();
      }
    }

    return state;
  }

  List<EquivalenceClass> splitEquivalenceClass(EquivalenceClass clazz) {
    assert clazz.representative() != null;
    List<EquivalenceClass> successors = NormalForms.toDnf(clazz.representative())
      .stream()
      .map(formulas -> getInitial(factory.of(Conjunction.of(formulas))))
      .collect(Collectors.toList());

    if (removeRedundantObligations) {
      //noinspection ObjectEquality
      successors.removeIf(x -> successors.stream().anyMatch(y -> x != y && x.implies(y)));
    }

    return successors;
  }

  static Formula unwrap(Formula formula) {
    return ((UnaryModalOperator) formula).operand;
  }

  private abstract static class Terminal extends EquivalenceClassStateFactory {
    private final EquivalenceClass initialState;

    private Terminal(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, false);
      this.initialState = super.getInitial(formula);
    }

    public final EquivalenceClass initialState() {
      return initialState;
    }

    public final EquivalenceClass initialStateWithRemainder(EquivalenceClass remainder) {
      return initialState.and(super.getInitial(remainder));
    }

    @Nullable
    public final Edge<EquivalenceClass> edge(EquivalenceClass clazz, BitSet valuation) {
      return makeEdge(super.successor(clazz, valuation));
    }

    public final Set<Edge<EquivalenceClass>> edges(EquivalenceClass clazz, BitSet valuation) {
      return Collections3.ofNullable(edge(clazz, valuation));
    }

    public final ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
      return super.successorTree(clazz, x -> Collections3.ofNullable(this.makeEdge(x)));
    }

    @Nullable
    protected abstract Edge<EquivalenceClass> makeEdge(EquivalenceClass successor);
  }

  public static final class CoSafety extends Terminal {
    public CoSafety(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, formula);
      Preconditions.checkArgument(SyntacticFragment.CO_SAFETY.contains(formula));
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> makeEdge(EquivalenceClass successor) {
      if (successor.isFalse()) {
        return null;
      }

      return successor.isTrue() ? Edge.of(successor, 0) : Edge.of(successor);
    }
  }

  public static final class Safety extends Terminal {
    public Safety(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, formula);
      Preconditions.checkArgument(SyntacticFragment.SAFETY.contains(formula));
    }

    @Override
    @Nullable
    protected Edge<EquivalenceClass> makeEdge(EquivalenceClass successor) {
      return successor.isFalse() ? null : Edge.of(successor);
    }
  }

  private abstract static class Looping extends EquivalenceClassStateFactory {
    protected final EquivalenceClass initialState;
    private final ValuationTree<EquivalenceClass> initialStateSuccessorTree;

    private Looping(EquivalenceClassFactory factory, boolean eagerUnfold, Formula formula,
      Predicate<Formula> isSupported) {
      super(factory, eagerUnfold, false);
      Preconditions.checkArgument(isSupported.test(formula));
      this.initialState = super.getInitial(unwrap(formula));
      this.initialStateSuccessorTree = super.successorTree(initialState);
    }

    public final EquivalenceClass initialState() {
      return initialState;
    }

    public final EquivalenceClass steppedInitialState() {
      // We avoid (or at least reduce the chances for) an unreachable initial state by eagerly
      // performing a single step.
      return edge(initialState(), new BitSet()).successor();
    }

    public final Edge<EquivalenceClass> edge(EquivalenceClass clazz, BitSet valuation) {
      var successor = super.successor(clazz, valuation);
      var initialStateSuccessors = initialStateSuccessorTree.get(valuation);
      assert initialStateSuccessors.size() == 1;
      return makeEdge(successor, initialStateSuccessors.iterator().next());
    }

    public final Set<Edge<EquivalenceClass>> edges(EquivalenceClass clazz, BitSet valuation) {
      return Set.of(edge(clazz, valuation));
    }

    public final ValuationTree<Edge<EquivalenceClass>> edgeTree(EquivalenceClass clazz) {
      var successorTree = super.successorTree(clazz);
      return cartesianProduct(successorTree, initialStateSuccessorTree, this::makeEdge);
    }

    protected abstract Edge<EquivalenceClass> makeEdge(
      EquivalenceClass successor, EquivalenceClass initialStateSuccessor);
  }

  public static final class GfCoSafety extends Looping {
    public GfCoSafety(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, formula, SyntacticFragments::isGfCoSafety);
    }

    @Override
    protected Edge<EquivalenceClass> makeEdge(
      EquivalenceClass successor, EquivalenceClass initialStateSuccessor) {
      if (!successor.isTrue()) {
        return Edge.of(successor);
      }

      if (!initialStateSuccessor.isTrue()) {
        return Edge.of(initialStateSuccessor, 0);
      }

      return Edge.of(initialState, 0);
    }
  }

  public static final class FgSafety extends Looping {
    public FgSafety(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, formula, SyntacticFragments::isFgSafety);
    }

    @Override
    protected Edge<EquivalenceClass> makeEdge(
      EquivalenceClass successor, EquivalenceClass initialStateSuccessor) {
      if (!successor.isFalse()) {
        return Edge.of(successor);
      }

      if (!initialStateSuccessor.isFalse()) {
        return Edge.of(initialStateSuccessor, 0);
      }

      return Edge.of(initialState, 0);
    }
  }

  private abstract static class Breakpoint extends EquivalenceClassStateFactory {
    protected final EquivalenceClass initialState;

    private Breakpoint(EquivalenceClassFactory factory, boolean eagerUnfold, Formula formula,
      Predicate<Formula> isSupported) {
      super(factory, eagerUnfold, false);
      Preconditions.checkArgument(isSupported.test(formula));
      this.initialState = super.getInitial(unwrap(formula));
    }

    @Nullable
    public final Edge<BreakpointState> edge(BreakpointState breakpointState, BitSet valuation) {
      var currentSuccessor = super.successor(breakpointState.current(), valuation);
      var nextSuccessor = super.successor(breakpointState.next(), valuation);
      return makeEdge(currentSuccessor, nextSuccessor);
    }

    public final Set<Edge<BreakpointState>> edges(BreakpointState breakpointState,
      BitSet valuation) {
      return Collections3.ofNullable(edge(breakpointState, valuation));
    }

    public final ValuationTree<Edge<BreakpointState>> edgeTree(BreakpointState breakpointState) {
      var currentSuccessorTree = super.successorTree(breakpointState.current());
      var nextSuccessorTree = super.successorTree(breakpointState.next());
      return cartesianProduct(currentSuccessorTree, nextSuccessorTree, this::makeEdge);
    }

    @Nullable
    protected abstract Edge<BreakpointState> makeEdge(EquivalenceClass current,
      EquivalenceClass next);
  }

  public static final class GCoSafety extends Breakpoint {
    public GCoSafety(EquivalenceClassFactory factory, boolean unfold, Formula formula) {
      super(factory, unfold, formula, x -> SyntacticFragments.isGCoSafety(x)
        && !(unwrap(formula) instanceof FOperator)
        && !(SyntacticFragment.FINITE.contains(unwrap(formula))));
    }

    public BreakpointState initialState() {
      return BreakpointState.of(initialState, factory.getTrue());
    }

    @Override
    @Nullable
    protected Edge<BreakpointState> makeEdge(EquivalenceClass current, EquivalenceClass next) {
      if (current.isTrue()) {
        return makeEdge(next.and(initialState), current, true);
      }

      if (current.isFalse() || next.isFalse()) {
        return null;
      }

      boolean accepting = false;
      EquivalenceClass newCurrent = current;
      EquivalenceClass newNext = next;

      if (current.implies(next)) {
        newNext = factory.getTrue();
      } else if (next.modalOperators().stream().allMatch(SyntacticFragment.FINITE::contains)) {
        newCurrent = current.and(next);
        newNext = factory.getTrue();

        if (current.modalOperators().stream().allMatch(SyntacticFragment.FINITE::contains)) {
          accepting = true;
        }
      }

      if (!newCurrent.and(newNext).implies(initialState)) {
        newNext = newNext.and(initialState);
      }

      return makeEdge(newCurrent, newNext, accepting);
    }

    @Nullable
    private static Edge<BreakpointState> makeEdge(EquivalenceClass current, EquivalenceClass next,
      boolean accepting) {
      if (current.isFalse() || next.isFalse()) {
        return null;
      }

      var successor = BreakpointState.of(current, next);
      return accepting ? Edge.of(successor, 0) : Edge.of(successor);
    }
  }

  @Value.Immutable
  @Tuple
  public abstract static class BreakpointState {
    public abstract EquivalenceClass current();

    public abstract EquivalenceClass next();

    public static BreakpointState of(EquivalenceClass current, EquivalenceClass next) {
      return BreakpointStateTuple.create(current, next);
    }
  }
}
