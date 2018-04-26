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

package owl.translations.ltl2ldba;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Conjunction;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.SyntacticFragment;
import owl.ltl.visitors.PropositionalVisitor;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public abstract class AbstractJumpManager<X extends RecurringObligation> {

  private static final Logger logger = Logger.getLogger(AbstractJumpManager.class.getName());
  private static final AnalysisResult<?> EMPTY = AnalysisResult.buildMay(Set.of());

  protected final EquivalenceClassFactory factory;
  protected final Set<Configuration> configuration;
  protected final Set<Formula> blockingModalOperators;

  protected AbstractJumpManager(Set<Configuration> configuration, EquivalenceClassFactory factory,
    Set<Formula> modalOperators, Formula initialFormula) {
    this.configuration = Set.copyOf(configuration);
    this.factory = factory;

    Set<Formula> unfilteredBlockingModalOperators = initialFormula
      .accept(BlockingModalOperatorsVisitor.INSTANCE);
    blockingModalOperators = unfilteredBlockingModalOperators.stream()
      .filter(x -> !isProperSubformula(x, modalOperators))
      .collect(Collectors.toUnmodifiableSet());
  }

  protected static <X> Stream<X> createDisjunctionStream(EquivalenceClass state,
    Function<Formula, Stream<X>> streamBuilder) {
    Formula representative = state.representative();

    if (!(representative instanceof Disjunction)) {
      return streamBuilder.apply(state.representative());
    }

    Disjunction disjunction = (Disjunction) representative;
    Stream<X> stream = Stream.empty();

    for (Formula disjunct : disjunction.children) {
      EquivalenceClass disjunctState = state.factory().of(disjunct);
      stream = Stream.concat(stream, streamBuilder.apply(disjunctState.representative()));
    }

    return stream;
  }

  @SuppressWarnings("ReferenceEquality")
  AnalysisResult<X> analyse(EquivalenceClass state) {
    AnalysisResult<X> result = checkTrivial(state);

    if (result != null) {
      return result;
    }

    Set<Jump<X>> jumps = computeJumps(state);

    logger.log(Level.FINE, () -> state + " has the following jumps: " + jumps);

    if (configuration.contains(Configuration.SUPPRESS_JUMPS)) {
      jumps.removeIf(jump -> jumps.stream().anyMatch(
        otherJump -> jump != otherJump && otherJump.containsLanguageOf(jump)));
    }

    logger.log(Level.FINE, () ->
      state + " has the following jumps (after language inclusion check): " + jumps);

    if (configuration.contains(Configuration.FORCE_JUMPS)) {
      for (Jump<X> jump : jumps) {
        EquivalenceClass jumpLanguage = jump.getLanguage();

        if (configuration.contains(Configuration.EAGER_UNFOLD)) {
          jumpLanguage = jumpLanguage.unfold();
        }

        if (state.equals(jumpLanguage)) {
          return AnalysisResult.buildMust(jump);
        }
      }
    }

    return AnalysisResult.buildMay(jumps);
  }

  protected Jump<X> buildJump(EquivalenceClass remainder, X obligations) {
    if (configuration.contains(Configuration.EAGER_UNFOLD)) {
      return new Jump<>(remainder.unfold(), obligations);
    }

    return new Jump<>(remainder, obligations);
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private AnalysisResult<X> checkTrivial(EquivalenceClass state) {
    Set<Formula> modalOperators = state.modalOperators();

    // The state is a simple safety or cosafety condition. We don't need to use reasoning about the
    // infinite behaviour and simply build the left-derivative of the formula.
    if (modalOperators.stream().allMatch(SyntacticFragment.CO_SAFETY::contains)
      || modalOperators.stream().allMatch(SyntacticFragment.SAFETY::contains)) {
      logger.log(Level.FINE, () -> state + " is (co)safety. Suppressing jump.");
      return (AnalysisResult<X>) EMPTY;
    }

    if (configuration.contains(Configuration.SUPPRESS_JUMPS)) {
      if (state.modalOperators().stream().anyMatch(blockingModalOperators::contains)) {
        return (AnalysisResult<X>) EMPTY;
      }
    }

    return null;
  }

  protected abstract Set<Jump<X>> computeJumps(EquivalenceClass state);

  private static boolean isProperSubformula(Formula formula, Collection<? extends Formula> set) {
    return set.stream().anyMatch(x -> !x.equals(formula) && x.anyMatch(formula::equals));
  }

  static class BlockingModalOperatorsVisitor extends PropositionalVisitor<Set<Formula>> {

    static final BlockingModalOperatorsVisitor INSTANCE = new BlockingModalOperatorsVisitor();

    @Override
    protected Set<Formula> modalOperatorAction(Formula formula) {
      if (SyntacticFragment.FINITE.contains(formula)) {
        return Set.of();
      }

      if (SyntacticFragment.CO_SAFETY.contains(formula)) {
        return Set.of(formula);
      }

      return Set.of();
    }

    @Override
    public Set<Formula> visit(BooleanConstant booleanConstant) {
      return Set.of();
    }

    @Override
    public Set<Formula> visit(Conjunction conjunction) {
      Set<Formula> blockingOperators = new HashSet<>();

      for (Formula child : conjunction.children) {
        // Only consider non-finite LTL formulas.
        if (!SyntacticFragment.FINITE.contains(child)) {
          blockingOperators.addAll(child.accept(this));
        }
      }

      return blockingOperators;
    }

    @Override
    public Set<Formula> visit(Disjunction disjunction) {
      Set<Formula> blockingOperators = null;

      for (Formula child : disjunction.children) {
        // Only consider non-finite LTL formulas.
        if (!SyntacticFragment.FINITE.contains(child)) {
          if (blockingOperators == null) {
            blockingOperators = new HashSet<>(child.accept(this));
          } else {
            blockingOperators.retainAll(child.accept(this));
          }
        }
      }

      return blockingOperators == null ? Set.of() : blockingOperators;
    }
  }
}