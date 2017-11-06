package owl.translations.ltl2ldba;

import static owl.translations.ltl2ldba.LTL2LDBAFunction.LOGGER;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.Literal;
import owl.translations.Optimisation;

public abstract class AbstractJumpManager<X extends RecurringObligation> {

  private static final AnalysisResult<?> EMPTY = AnalysisResult.buildMay(ImmutableSet.of());

  protected final EquivalenceClassFactory factory;
  protected final ImmutableSet<Optimisation> optimisations;

  public AbstractJumpManager(Set<Optimisation> optimisations, EquivalenceClassFactory factory) {
    this.optimisations = ImmutableSet.copyOf(optimisations);
    this.factory = factory;
  }

  protected static <X> Stream<X> createDisjunctionStream(EquivalenceClass state,
    Function<EquivalenceClass, Stream<X>> streamBuilder) {
    Formula representative = state.getRepresentative();

    if (!(representative instanceof Disjunction)) {
      return streamBuilder.apply(state);
    }

    Disjunction disjunction = (Disjunction) representative;
    Stream<X> stream = Stream.empty();

    for (Formula disjunct : disjunction.children) {
      EquivalenceClass disjunctState = state.getFactory().createEquivalenceClass(disjunct);
      stream = Stream.concat(stream, createDisjunctionStream(disjunctState, streamBuilder));
    }

    return stream;
  }

  /* Literals are differently encoded in support */
  public static boolean equalsInSupport(Formula formula1, Formula formula2) {
    return formula1.equals(formula2) || formula1 instanceof Literal && formula2 instanceof Literal
      && ((Literal) formula1).getAtom() == ((Literal) formula2).getAtom();
  }

  AnalysisResult<X> analyse(EquivalenceClass state) {
    AnalysisResult<X> result = checkTrivial(state);

    if (result != null) {
      return result;
    }

    Set<Jump<X>> jumps = computeJumps(state);

    LOGGER.log(Level.FINE, () -> state + " has the following jumps: " + jumps);

    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      jumps.removeIf(jump -> jumps.stream().anyMatch(
        otherJump -> jump != otherJump && otherJump.containsLanguageOf(jump)));
    }

    LOGGER.log(Level.FINE, () ->
      state + " has the following jumps (after language inclusion check): " + jumps);

    if (optimisations.contains(Optimisation.FORCE_JUMPS)) {
      for (Jump<X> jump : jumps) {
        EquivalenceClass jumpLanguage = jump.getLanguage();

        if (optimisations.contains(Optimisation.EAGER_UNFOLD)) {
          jumpLanguage = jumpLanguage.unfold();
        }

        boolean stateLanguageIsContained = state.implies(jumpLanguage);
        jumpLanguage.free();

        if (stateLanguageIsContained) {
          return AnalysisResult.buildMust(jump);
        }
      }
    }

    return AnalysisResult.buildMay(jumps);
  }

  protected Jump<X> buildJump(EquivalenceClass remainder, X obligations) {
    if (optimisations.contains(Optimisation.EAGER_UNFOLD)) {
      return new Jump<>(remainder.unfold(), obligations);
    }

    return new Jump<>(remainder, obligations);
  }

  @Nullable
  private AnalysisResult<X> checkTrivial(EquivalenceClass state) {
    // The state is a simple safety or cosafety condition. We don't need to use reasoning about the
    // infinite behaviour and simply build the left-derivative of the formula.
    if (state.testSupport(Fragments::isCoSafety) || state.testSupport(Fragments::isSafety)) {
      LOGGER.log(Level.FINE, () -> state + " is (co)safety. Suppressing jump.");
      return (AnalysisResult<X>) EMPTY;
    }

    /* This violates the LDBA-property, but can be integrated into LTL2DPA using a heuristics
     * interface.
     *
     * if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
     *   EquivalenceClass safetyCore = state.substitue(
     *     (x) -> Fragements.isSafety(x) ? BooleanConstant.True : x);
     *   boolean existsSafetyCore = safety.isTrue();
     *   safetyCore.free();
     *
     *   if (existsSafetyCore) {
     *     return EMPTY;
     *   }
     * }
     */

    // Check if the state depends on an independent cosafety property.
    if (optimisations.contains(Optimisation.MINIMIZE_JUMPS)) {
      Set<Formula> notCoSafety = state.getSupport(x -> !Fragments.isCoSafety(x));

      EquivalenceClass coSafety = state.exists(x -> {
        for (Formula formula : notCoSafety) {
          if (formula.anyMatch(z -> equalsInSupport(x, z))) {
            return true;
          }
        }

        return false;
      });

      boolean existsExternalCondition = !coSafety.isTrue();
      coSafety.free();

      if (existsExternalCondition) {
        LOGGER.log(Level.FINE, state + " has independent cosafety property. Suppressing jump.");
        return (AnalysisResult<X>) EMPTY;
      }
    }

    return null;
  }

  protected abstract Set<Jump<X>> computeJumps(EquivalenceClass state);
}