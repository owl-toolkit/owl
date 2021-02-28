/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.translations;

import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPLEMENT_CONSTRUCTION_EXACT;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.COMPRESS_COLOURS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.OPTIMISE_INITIAL_STATE;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import owl.Bibliography;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.degeneralization.RabinDegeneralization;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.cinterface.CInterface;
import owl.ltl.LabelledFormula;
import owl.ltl.rewriter.SimplifierFactory;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.canonical.NonDeterministicConstructionsPortfolio;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.NormalformDPAConstruction;
import owl.translations.ltl2dra.NormalformDRAConstruction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.ltl2ldba.AnnotatedLDBA;
import owl.translations.ltl2ldba.AsymmetricLDBAConstruction;
import owl.translations.ltl2ldba.SymmetricLDBAConstruction;
import owl.translations.ltl2nba.SymmetricNBAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;
import owl.util.ParallelEvaluation;

/**
 * Central repository of all implemented LTL translations.
 */
@CContext(CInterface.CDirectives.class)
public final class LtlTranslationRepository {

  private LtlTranslationRepository() {}

  public static <A extends EmersonLeiAcceptance>
    Function<LabelledFormula, Automaton<?, ? extends A>>
    defaultTranslation(BranchingMode branchingMode, Class<? extends A> acceptance) {

    return defaultTranslation(EnumSet.allOf(Option.class), branchingMode, acceptance);
  }

  public static <A extends EmersonLeiAcceptance>
    Function<LabelledFormula, Automaton<?, ? extends A>>
    defaultTranslation(
      Set<Option> translationOptions, BranchingMode branchingMode, Class<? extends A> acceptance) {

    Function<LabelledFormula, ? extends Automaton<?, ?>> translation = null;

    switch (branchingMode) {
      case NON_DETERMINISTIC:
        if (GeneralizedBuchiAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToNbaTranslation.DEFAULT.translation(
            acceptance.asSubclass(GeneralizedBuchiAcceptance.class),
            translationOptions);
        }

        break;

      case LIMIT_DETERMINISTIC:
        if (GeneralizedBuchiAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToLdbaTranslation.DEFAULT.translation(
            acceptance.asSubclass(GeneralizedBuchiAcceptance.class),
            translationOptions);
        }

        break;

      case DETERMINISTIC:
        if (ParityAcceptance.class.equals(acceptance)) {
          translation = LtlToDpaTranslation.DEFAULT.translation(
            ParityAcceptance.class,
            translationOptions);
        } else if (GeneralizedRabinAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToDraTranslation.DEFAULT.translation(
            acceptance.asSubclass(GeneralizedRabinAcceptance.class),
            translationOptions);
        } else if (EmersonLeiAcceptance.class.equals(acceptance)) {
          translation = LtlToDelaTranslation.DEFAULT.translation(
            EmersonLeiAcceptance.class,
            translationOptions);
        }

        break;

      default:
        throw new AssertionError("unreachable.");
    }

    if (translation == null) {
      throw iae();
    }

    var finalTranslation = translation;
    return x -> OmegaAcceptanceCast.cast(finalTranslation.apply(x), acceptance);
  }

  public static <A extends EmersonLeiAcceptance>
    Function<LabelledFormula, Automaton<?, ? extends A>>
    smallestAutomaton(BranchingMode branchingMode, Class<? extends A> acceptance) {

    Function<LabelledFormula, ? extends Automaton<?, ?>> translation = null;
    Set<Option> translationOptions = EnumSet.allOf(Option.class);

    switch (branchingMode) {
      case NON_DETERMINISTIC:
        if (GeneralizedBuchiAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToNbaTranslation.EKS20.translation(
            acceptance.asSubclass(GeneralizedBuchiAcceptance.class),
            translationOptions);
        } else if (OmegaAcceptanceCast.isInstanceOf(GeneralizedBuchiAcceptance.class, acceptance)
          || OmegaAcceptanceCast.isInstanceOf(BuchiAcceptance.class, acceptance)) {

          boolean generalised
            = OmegaAcceptanceCast.isInstanceOf(GeneralizedBuchiAcceptance.class, acceptance);
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
            BranchingMode.NON_DETERMINISTIC, translationOptions, acceptance);

          translation = labelledFormula -> {
            Supplier<Optional<Automaton<?, ?>>> eks20translation = () -> Optional.of(generalised
              ? LtlToNbaTranslation.EKS20.translation(
              GeneralizedBuchiAcceptance.class, copiedTranslationOptions).apply(labelledFormula)
              : LtlToNbaTranslation.EKS20.translation(
                BuchiAcceptance.class, copiedTranslationOptions).apply(labelledFormula));

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
              List.of(
                () -> (Optional) portfolioTranslation.apply(labelledFormula),
                eks20translation
              )));
          };
        }

        break;

      case LIMIT_DETERMINISTIC:
        if (GeneralizedBuchiAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToLdbaTranslation.SMALLEST_AUTOMATON.translation(
            acceptance.asSubclass(GeneralizedBuchiAcceptance.class),
            translationOptions);
        }

        break;

      case DETERMINISTIC:
        if (ParityAcceptance.class.equals(acceptance)) {
          translation = LtlToDpaTranslation.SMALLEST_AUTOMATON.translation(
            ParityAcceptance.class,
            translationOptions);
        } else if (GeneralizedRabinAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToDraTranslation.SMALLEST_AUTOMATON.translation(
            acceptance.asSubclass(GeneralizedRabinAcceptance.class),
            translationOptions);
        } else if (EmersonLeiAcceptance.class.isAssignableFrom(acceptance)) {
          translation = LtlToDelaTranslation.MS17.translation(
            EmersonLeiAcceptance.class,
            translationOptions);
        }

        break;

      default:
        throw new AssertionError("unreachable.");
    }

    if (translation == null) {
      throw iae();
    }

    var finalTranslation = translation;

    return labelledFormula -> {
      var automaton = finalTranslation.apply(labelledFormula);
      assert acceptance.isInstance(automaton.acceptance());
      @SuppressWarnings("unchecked")
      var castedAutomaton = (Automaton<?, ? extends A>) automaton;
      return castedAutomaton;
    };
  }

  public interface LtlTranslation<L extends U, U extends EmersonLeiAcceptance> {

    Class<L> acceptanceClassLowerBound();

    Class<U> acceptanceClassUpperBound();

    default boolean acceptanceClassWithinBounds(Class<? extends U> acceptanceClass) {
      return acceptanceClass.isAssignableFrom(acceptanceClassLowerBound())
        && acceptanceClassUpperBound().isAssignableFrom(acceptanceClass);
    }

    default String citeKey() {
      return toString();
    }

    default Function<LabelledFormula, Automaton<?, ? extends U>>
      translation() {

      return translation(acceptanceClassUpperBound());
    }

    default Function<LabelledFormula, Automaton<?, ? extends U>>
      translation(Set<Option> translationOptions) {

      return translation(acceptanceClassUpperBound(), translationOptions);
    }

    default <A extends U> Function<LabelledFormula, Automaton<?, ? extends A>>
      translation(Class<A> acceptanceClass) {

      return translation(acceptanceClass, EnumSet.allOf(Option.class));
    }

    <A extends U> Function<LabelledFormula, Automaton<?, ? extends A>>
      translation(Class<A> acceptanceClass, Set<Option> translationOptions);

  }

  public enum BranchingMode {
    NON_DETERMINISTIC,
    LIMIT_DETERMINISTIC,
    DETERMINISTIC
  }

  @CEnum("ltl_translation_option_t")
  public enum Option {
    SIMPLIFY_FORMULA,
    SIMPLIFY_AUTOMATON,
    USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS,
    USE_COMPLEMENT,
    USE_DUAL;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native Option fromCValue(int value);
  }

  private static IllegalArgumentException iae() {
    return new IllegalArgumentException("Unsupported Combination");
  }

  private static void assertCiteKey(String expected, String actual) {
    int lastIndex = actual.lastIndexOf('_');
    var normalisedActual = actual.substring(lastIndex + 1);
    assert expected.equals(normalisedActual);
    assert Bibliography.INDEX.containsKey(expected);
  }

  public enum LtlToNbaTranslation
    implements LtlTranslation<BuchiAcceptance, GeneralizedBuchiAcceptance> {

    EKS20(Bibliography.JACM_20_CITEKEY);

    public static final LtlTranslationRepository.LtlToNbaTranslation DEFAULT = EKS20;

    LtlToNbaTranslation(String citeKey) {
      assertCiteKey(citeKey, citeKey());
    }

    @Override
    public Class<BuchiAcceptance> acceptanceClassLowerBound() {
      return BuchiAcceptance.class;
    }

    @Override
    public Class<GeneralizedBuchiAcceptance> acceptanceClassUpperBound() {
      return GeneralizedBuchiAcceptance.class;
    }

    @Override
    public <B extends GeneralizedBuchiAcceptance>
      Function<LabelledFormula, Automaton<?, ? extends B>>
      translation(Class<B> acceptanceClass, Set<Option> translationOptions) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      assert this == EKS20;

      return applyPreAndPostProcessing(
        SymmetricNBAConstruction.of(acceptanceClass),
        BranchingMode.NON_DETERMINISTIC,
        translationOptions,
        acceptanceClass);
    }
  }

  public enum LtlToLdbaTranslation
    implements LtlTranslation<BuchiAcceptance, GeneralizedBuchiAcceptance> {

    SEJK16(Bibliography.CAV_16_CITEKEY),
    EKS20(Bibliography.JACM_20_CITEKEY),
    SMALLEST_AUTOMATON(null);

    public static final LtlToLdbaTranslation DEFAULT = SEJK16;

    LtlToLdbaTranslation(@Nullable String citeKey) {
      if (citeKey != null) {
        assertCiteKey(citeKey, citeKey());
      }
    }

    @Override
    public Class<BuchiAcceptance> acceptanceClassLowerBound() {
      return BuchiAcceptance.class;
    }

    @Override
    public Class<GeneralizedBuchiAcceptance> acceptanceClassUpperBound() {
      return GeneralizedBuchiAcceptance.class;
    }

    @Override
    public <B extends GeneralizedBuchiAcceptance>
      Function<LabelledFormula, Automaton<?, ? extends B>>
      translation(Class<B> acceptanceClass, Set<Option> translationOptions) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case SEJK16:
          return applyPreAndPostProcessing(
            AsymmetricLDBAConstruction.of(acceptanceClass).andThen(AnnotatedLDBA::copyAsMutable),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case EKS20:
          return applyPreAndPostProcessing(
            SymmetricLDBAConstruction.of(acceptanceClass)::applyWithShortcuts,
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case SMALLEST_AUTOMATON:
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
            BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);

          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends B>>> sejk16translation = () -> Optional.of(
              SEJK16.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends B>>> eks20translation = () -> Optional.of(
              EKS20.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
              List.of(
                () -> portfolioTranslation.apply(labelledFormula),
                sejk16translation,
                eks20translation
              )));
          };

        default:
          throw new AssertionError("Unreachable.");
      }
    }
  }

  @CEnum("ltl_to_dpa_translation_t")
  public enum LtlToDpaTranslation implements LtlTranslation<ParityAcceptance, ParityAcceptance> {

    SEJK16_EKRS17(Bibliography.TACAS_17_1_CITEKEY),
    EKS20_EKRS17(Bibliography.TACAS_17_1_CITEKEY),
    UNPUBLISHED_ZIELONKA(null),
    SMALLEST_AUTOMATON(null);

    // TODO: Add constructions going through DRAs.

    public static final LtlToDpaTranslation DEFAULT = SEJK16_EKRS17;

    LtlToDpaTranslation(@Nullable String citeKey) {
      if (citeKey != null) {
        assertCiteKey(citeKey, citeKey());
      }
    }

    @Override
    public Class<ParityAcceptance> acceptanceClassLowerBound() {
      return ParityAcceptance.class;
    }

    @Override
    public Class<ParityAcceptance> acceptanceClassUpperBound() {
      return ParityAcceptance.class;
    }

    @Override
    public <A extends ParityAcceptance> Function<LabelledFormula, Automaton<?, ? extends A>>
      translation(Class<A> acceptanceClass, Set<Option> translationOptions) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case SEJK16_EKRS17: {
          var configuration
            = EnumSet.of(OPTIMISE_INITIAL_STATE, COMPRESS_COLOURS);

          if (translationOptions.contains(Option.USE_COMPLEMENT)) {
            configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
          }

          var ekrs17translation = new LTL2DPAFunction(configuration);

          return applyPreAndPostProcessing(
            x -> OmegaAcceptanceCast.cast(ekrs17translation.apply(x), acceptanceClass),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);
        }

        case EKS20_EKRS17: {
          var configuration
            = EnumSet.of(SYMMETRIC, OPTIMISE_INITIAL_STATE, COMPRESS_COLOURS);

          if (translationOptions.contains(Option.USE_COMPLEMENT)) {
            configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
          }

          var ekrs17translation = new LTL2DPAFunction(configuration);

          return applyPreAndPostProcessing(
            x -> OmegaAcceptanceCast.cast(ekrs17translation.apply(x), acceptanceClass),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);
        }

        case UNPUBLISHED_ZIELONKA: {
          var translation = NormalformDPAConstruction.of(false);

          return applyPreAndPostProcessing(
            x -> OmegaAcceptanceCast.cast(translation.apply(x), acceptanceClass),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass
          );
        }

        case SMALLEST_AUTOMATON:
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.USE_COMPLEMENT);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
            BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);

          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends A>>> sejk16translation = () -> Optional.of(
              SEJK16_EKRS17.translation(acceptanceClass, copiedTranslationOptions)
                .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends A>>> eks20translation = () -> Optional.of(
              EKS20_EKRS17.translation(acceptanceClass, copiedTranslationOptions)
                .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends A>>> translation = () -> Optional.of(
              UNPUBLISHED_ZIELONKA.translation(acceptanceClass, copiedTranslationOptions)
                .apply(labelledFormula));

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
              List.of(
                () -> portfolioTranslation.apply(labelledFormula),
                sejk16translation,
                eks20translation,
                translation
              )));
          };

        default:
          throw new AssertionError("Unreachable.");
      }
    }

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native LtlToDpaTranslation fromCValue(int value);
  }

  public enum LtlToDraTranslation
    implements LtlTranslation<RabinAcceptance, GeneralizedRabinAcceptance> {

    EKS16(Bibliography.FMSD_16_CITEKEY),
    EKS20(Bibliography.JACM_20_CITEKEY),
    SE20(Bibliography.LICS_20_CITEKEY),
    SMALLEST_AUTOMATON(null);

    public static final LtlToDraTranslation DEFAULT = EKS20;

    LtlToDraTranslation(@Nullable String citeKey) {
      if (citeKey != null) {
        assertCiteKey(citeKey, citeKey());
      }
    }

    @Override
    public Class<RabinAcceptance> acceptanceClassLowerBound() {
      return RabinAcceptance.class;
    }

    @Override
    public Class<GeneralizedRabinAcceptance> acceptanceClassUpperBound() {
      return GeneralizedRabinAcceptance.class;
    }

    @Override
    public <R extends GeneralizedRabinAcceptance>
      Function<LabelledFormula, Automaton<?, ? extends R>>
      translation(Class<R> acceptanceClass, Set<Option> translationOptions) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case EKS16:
          Function<LabelledFormula, Automaton<?, ? extends R>> eks16construction =
          labelledFormula -> {

            var dgra = RabinizerBuilder.build(labelledFormula,
              RabinizerConfiguration.of(true, true, true));

            if (acceptanceClass.equals(GeneralizedRabinAcceptance.class)) {
              return OmegaAcceptanceCast.cast(dgra, acceptanceClass);
            }

            return OmegaAcceptanceCast.cast(
              RabinDegeneralization.degeneralize(AcceptanceOptimizations.optimize(dgra)),
              acceptanceClass);
          };

          return applyPreAndPostProcessing(
            eks16construction,
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case EKS20:
          return applyPreAndPostProcessing(
            SymmetricDRAConstruction.of(acceptanceClass, true),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case SE20:
          return applyPreAndPostProcessing(
            NormalformDRAConstruction.of(
              acceptanceClass,
              translationOptions.contains(Option.USE_DUAL)),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case SMALLEST_AUTOMATON:
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.USE_COMPLEMENT);
          copiedTranslationOptions.add(Option.USE_DUAL);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
            BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);

          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends R>>> sejk16translation = () -> Optional.of(
              EKS16.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends R>>> eks20translation = () -> Optional.of(
              EKS20.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends R>>> se20translation = () -> Optional.of(
              SE20.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends R>>> dpaTranslation = () -> Optional.of(
              OmegaAcceptanceCast.cast(
                LtlToDpaTranslation.SMALLEST_AUTOMATON.translation(
                  ParityAcceptance.class,
                  copiedTranslationOptions).apply(labelledFormula),
                acceptanceClass)
            );

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
              List.of(
                () -> portfolioTranslation.apply(labelledFormula),
                sejk16translation,
                eks20translation,
                se20translation,
                dpaTranslation
              )));
          };

        default:
          throw new AssertionError("Unreachable.");
      }
    }
  }

  public enum LtlToDelaTranslation
    implements LtlTranslation<EmersonLeiAcceptance, EmersonLeiAcceptance> {

    MS17(Bibliography.GANDALF_17_CITEKEY),
    SMALLEST_AUTOMATON(null);

    public static final LtlToDelaTranslation DEFAULT = MS17;

    LtlToDelaTranslation(@Nullable String citeKey) {
      if (citeKey != null) {
        assertCiteKey(citeKey, citeKey());
      }
    }

    @Override
    public Class<EmersonLeiAcceptance> acceptanceClassLowerBound() {
      return EmersonLeiAcceptance.class;
    }

    @Override
    public Class<EmersonLeiAcceptance> acceptanceClassUpperBound() {
      return EmersonLeiAcceptance.class;
    }

    @Override
    public <A extends EmersonLeiAcceptance> Function<LabelledFormula, Automaton<?, ? extends A>>
      translation(Class<A> acceptanceClass, Set<Option> translationOptions) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case MS17:
          return applyPreAndPostProcessing(
            x -> OmegaAcceptanceCast.cast(new DelagBuilder().apply(x), acceptanceClass),
            BranchingMode.DETERMINISTIC,
            translationOptions,
            acceptanceClass);

        case SMALLEST_AUTOMATON:
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.USE_COMPLEMENT);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
            BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);

          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends A>>> ms17translation = () -> Optional.of(
              MS17.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends A>>> dgraTranslation = () -> Optional.of(
              OmegaAcceptanceCast.cast(
                LtlToDraTranslation.SMALLEST_AUTOMATON.translation(
                  GeneralizedRabinAcceptance.class,
                  copiedTranslationOptions).apply(labelledFormula),
                acceptanceClass)
            );

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
              List.of(
                () -> portfolioTranslation.apply(labelledFormula),
                ms17translation,
                dgraTranslation
              )));
          };

        default:
          throw new AssertionError("Unreachable.");
      }
    }
  }

  private static <A extends EmersonLeiAcceptance>
    Function<LabelledFormula, Automaton<?, ? extends A>>
    applyPreAndPostProcessing(
      Function<LabelledFormula, ? extends Automaton<?, ? extends A>> function,
      BranchingMode branchingMode,
      Set<Option> translationOptions,
      Class<A> acceptanceCondition) {

    boolean simplifyFormula = translationOptions.contains(Option.SIMPLIFY_FORMULA);
    boolean simplifyAutomaton = translationOptions.contains(Option.SIMPLIFY_AUTOMATON);

    Function<LabelledFormula, ? extends Automaton<?, ? extends A>> wrappedFunction;

    if (translationOptions.contains(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)) {

      var portfolio = branchingMode == BranchingMode.NON_DETERMINISTIC
        ? new NonDeterministicConstructionsPortfolio<>(acceptanceCondition)
        : new DeterministicConstructionsPortfolio<>(acceptanceCondition);

      wrappedFunction = labelledFormula ->
        portfolio.apply(labelledFormula).orElseGet(() -> function.apply(labelledFormula));
    } else {
      wrappedFunction = function;
    }

    return unprocessedFormula -> {
      var formula = simplifyFormula
        ? SimplifierFactory.apply(unprocessedFormula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT)
        : unprocessedFormula;
      var automaton = wrappedFunction.apply(formula);
      return simplifyAutomaton
        ? AcceptanceOptimizations.optimize(automaton)
        : automaton;
    };
  }

  private static <A extends EmersonLeiAcceptance>
    Function<LabelledFormula, Optional<Automaton<?, ? extends A>>>
    portfolioWithPreAndPostProcessing(
      BranchingMode branchingMode,
      Set<Option> translationOptions,
      Class<A> acceptanceCondition) {

    if (!translationOptions.contains(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)) {
      return x -> Optional.empty();
    }

    boolean simplifyFormula = translationOptions.contains(Option.SIMPLIFY_FORMULA);
    boolean simplifyAutomaton = translationOptions.contains(Option.SIMPLIFY_AUTOMATON);

    var portfolio = branchingMode == BranchingMode.NON_DETERMINISTIC
      ? new NonDeterministicConstructionsPortfolio<>(acceptanceCondition)
      : new DeterministicConstructionsPortfolio<>(acceptanceCondition);

    return unprocessedFormula -> {
      var formula = simplifyFormula
        ? SimplifierFactory.apply(unprocessedFormula, SimplifierFactory.Mode.SYNTACTIC_FIXPOINT)
        : unprocessedFormula;
      var automaton = portfolio.apply(formula);
      return simplifyAutomaton
        ? automaton.map(AcceptanceOptimizations::optimize)
        : automaton;
    };
  }
}
