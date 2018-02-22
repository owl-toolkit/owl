package owl.translations.ltl2ldba;

import com.google.common.collect.ImmutableSet;
import java.util.BitSet;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import owl.factories.EquivalenceClassFactory;
import owl.ltl.BooleanConstant;
import owl.ltl.Disjunction;
import owl.ltl.EquivalenceClass;
import owl.ltl.Formula;
import owl.ltl.Fragments;
import owl.ltl.visitors.Collector;
import owl.translations.ltl2ldba.LTL2LDBAFunction.Configuration;

public abstract class AbstractJumpManager<X extends RecurringObligation> {

  private static final Logger logger = Logger.getLogger(AbstractJumpManager.class.getName());
  private static final AnalysisResult<?> EMPTY = AnalysisResult.buildMay(Set.of());

  protected final EquivalenceClassFactory factory;
  protected final ImmutableSet<Configuration> configuration;

  public AbstractJumpManager(ImmutableSet<Configuration> configuration,
    EquivalenceClassFactory factory) {
    this.configuration = configuration;
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
      EquivalenceClass disjunctState = state.getFactory().of(disjunct);
      stream = Stream.concat(stream, streamBuilder.apply(disjunctState));
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

        if (state.implies(jumpLanguage)) {
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
    // The state is a simple safety or cosafety condition. We don't need to use reasoning about the
    // infinite behaviour and simply build the left-derivative of the formula.
    if (state.testSupport(Fragments::isCoSafety) || state.testSupport(Fragments::isSafety)) {
      logger.log(Level.FINE, () -> state + " is (co)safety. Suppressing jump.");
      return (AnalysisResult<X>) EMPTY;
    }

    // Check if the state depends on an independent cosafety property.
    if (configuration.contains(Configuration.SUPPRESS_JUMPS)) {
      BitSet nonCoSafety = Collector.collectAtoms(state.getSupport(x -> !Fragments.isCoSafety(x)));

      EquivalenceClass core = state.substitute(x -> {
        if (!Fragments.isCoSafety(x)) {
          return BooleanConstant.TRUE;
        }

        BitSet ap = Collector.collectAtoms(x);
        assert !ap.isEmpty() : "Formula " + x + " has empty AP.";
        return ap.intersects(nonCoSafety) ? x : BooleanConstant.FALSE;
      });

      if (core.isFalse()) {
        logger.log(Level.FINE, state + " has independent cosafety property. Suppressing jump.");
        return (AnalysisResult<X>) EMPTY;
      }
    }

    return null;
  }

  protected abstract Set<Jump<X>> computeJumps(EquivalenceClass state);
}