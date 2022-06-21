/*
 * Copyright (C) 2021, 2022  (Salomon Sickert)
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
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.POST_PROCESS;
import static owl.translations.ltl2dpa.LTL2DPAFunction.Configuration.SYMMETRIC;

import com.google.common.base.Preconditions;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumConstant;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;
import owl.Bibliography;
import owl.automaton.Automaton;
import owl.automaton.Views;
import owl.automaton.acceptance.AllAcceptance;
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
import owl.ltl.rewriter.SimplifierRepository;
import owl.translations.canonical.DeterministicConstructionsPortfolio;
import owl.translations.canonical.NonDeterministicConstructionsPortfolio;
import owl.translations.delag.DelagBuilder;
import owl.translations.ltl2dela.NormalformDELAConstruction;
import owl.translations.ltl2dpa.LTL2DPAFunction;
import owl.translations.ltl2dpa.NormalformDPAConstruction;
import owl.translations.ltl2dpa.SymbolicDPAConstruction;
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

  private LtlTranslationRepository() {
  }

  public static <A extends EmersonLeiAcceptance>
  Function<LabelledFormula, Automaton<?, ? extends A>>
  defaultTranslation(BranchingMode branchingMode, Class<? extends A> acceptance) {

    return defaultTranslation(Option.defaultOptions(), branchingMode, acceptance);
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
    Set<Option> translationOptions = Option.defaultOptions();

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

      return translation(acceptanceClass, Option.defaultOptions());
    }

    default <A extends U> Function<LabelledFormula, Automaton<?, ? extends A>>
    translation(Class<A> acceptanceClass, Set<Option> translationOptions) {

      return translation(acceptanceClass, translationOptions, OptionalInt.empty());
    }

    <A extends U> Function<LabelledFormula, Automaton<?, ? extends A>>
    translation(Class<A> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead);

  }

  public enum BranchingMode {
    NON_DETERMINISTIC,
    LIMIT_DETERMINISTIC,
    DETERMINISTIC
  }

  @CEnum("owl_ltl_translation_option")
  public enum Option {

    /**
     * Simplify the formula before applying the translation.
     */
    @CEnumConstant("OWL_SIMPLIFY_FORMULA")
    SIMPLIFY_FORMULA,

    /**
     * Simplify the automaton, e.g. remove non-accepting states. Note that this causes a full
     * exploration of the automaton.
     */
    @CEnumConstant("OWL_SIMPLIFY_AUTOMATON")
    SIMPLIFY_AUTOMATON,

    /**
     * Use a portfolio of simpler constructions for fragments of LTL.
     */
    @CEnumConstant("OWL_USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS")
    USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS,

    /**
     * Ensures that the transition relation of the automaton is complete.
     */
    @CEnumConstant("OWL_COMPLETE")
    COMPLETE,

    @CEnumConstant("OWL_X_DPA_USE_COMPLEMENT")
    X_DPA_USE_COMPLEMENT,

    @CEnumConstant("OWL_X_DRA_NORMAL_FORM_USE_DUAL")
    X_DRA_NORMAL_FORM_USE_DUAL;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native Option fromCValue(int value);

    public static Set<Option> defaultOptions() {
      var defaultOptions = EnumSet.allOf(Option.class);
      defaultOptions.remove(COMPLETE);
      return defaultOptions;
    }
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

    public static final String EKS20_DESCRIPTION = "Translate the formula to a non-deterministic "
        + "(generalised) Büchi automaton by guessing and checking the set of greatest fixed-point "
        + "operators, i.e. G, R, and W, that is satisfied by almost all suffixes of the word read by "
        + "the automaton and the set of least fixed-point operators, i.e. F, M, and U, that is "
        + "satisfied by infinitely many suffixes of the word read by the automaton. This construction"
        + " has been initially proposed in [" + Bibliography.LICS_18_CITEKEY + "] and has been "
        + "described in more detail and with optimisations in ["
        + Bibliography.DISSERTATION_19_CITEKEY
        + "]. The preferred reference to cite is the journal "
        + "article [" + Bibliography.JACM_20_CITEKEY + "]. The translation used to be available "
        + "through the '--symmetric' option.";

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
    translation(Class<B> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead) {

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

    public static final String SEJK16_DESCRIPTION = "Translate the formula to a "
        + "limit-deterministic (generalised) Büchi automaton by guessing and checking the set of "
        + "greatest fixed-point operators, i.e. G, R, and W, that is satisfied by almost all "
        + "suffixes of the word read by the automaton. The construction is an optimised version of "
        + "the construction appearing in [" + Bibliography.CAV_16_CITEKEY + "] and used to be "
        + "available through the '--asymmetric' option.";

    public static final String EKS20_DESCRIPTION = "Translate the formula to a limit-deterministic "
        + "(generalised) Büchi automaton by guessing and checking the set of greatest fixed-point "
        + "operators, i.e. G, R, and W, that is satisfied by almost all suffixes of the word read by "
        + "the automaton and the set of least fixed-point operators, i.e. F, M, and U, that is "
        + "satisfied by infinitely many suffixes of the word read by the automaton. This construction"
        + " has been initially proposed in [" + Bibliography.LICS_18_CITEKEY + "] and has been "
        + "described in more detail and with optimisations in ["
        + Bibliography.DISSERTATION_19_CITEKEY
        + "]. The preferred reference to cite is the journal "
        + "article [" + Bibliography.JACM_20_CITEKEY + "]. The translation used to be available "
        + "through the '--symmetric' option.";

    public static final String SMALLEST_AUTOMATON_DESCRIPTION
        = "Run all available LD(G)BA-translations in parallel and return the smallest automaton.";

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
    translation(Class<B> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead) {

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
                SEJK16.translation(acceptanceClass, copiedTranslationOptions)
                    .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends B>>> eks20translation = () -> Optional.of(
                EKS20.translation(acceptanceClass, copiedTranslationOptions)
                    .apply(labelledFormula));

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

  @CEnum("owl_ltl_to_dpa_translation")
  public enum LtlToDpaTranslation implements LtlTranslation<ParityAcceptance, ParityAcceptance> {

    @CEnumConstant("OWL_LTL_TO_DPA_SEJK16_EKRS17")
    SEJK16_EKRS17(Bibliography.TACAS_17_1_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DPA_EKS20_EKRS17")
    EKS20_EKRS17(Bibliography.TACAS_17_1_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DPA_SYMBOLIC_SE20_BKS10")
    SYMBOLIC_SE20_BKS10(Bibliography.FSTTCS_10_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DPA_SLM21")
    SLM21(Bibliography.UNDER_SUBMISSION_21_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DPA_SMALLEST_AUTOMATON")
    SMALLEST_AUTOMATON(null);

    public static final LtlToDpaTranslation DEFAULT = SLM21;

    public static final String SEJK16_EKRS17_DESCRIPTION = "Translate the formula to a "
        + "deterministic parity automaton by combining [" + Bibliography.CAV_16_CITEKEY + "] with "
        + "the LDBA-to-DPA translation of [" + Bibliography.TACAS_17_1_CITEKEY + "]. This "
        + "translation used to be available through the '--asymmetric' option.";

    public static final String EKS20_EKRS17_DESCRIPTION = "Translate the formula to a "
        + "deterministic parity automaton by combining [" + Bibliography.JACM_20_CITEKEY + "] with "
        + "the LDBA-to-DPA translation of [" + Bibliography.TACAS_17_1_CITEKEY + "]. This "
        + "translation used to be available through the '--symmetric' option.";

    public static final String SYMBOLIC_SE20_BKS10_DESCRIPTION = "Translate the formula to a "
        + "deterministic parity automaton by combining the LTL-to-DRA translation of ["
        + Bibliography.LICS_20_CITEKEY + "] with DRAxDSA-to-DPA result of ["
        + Bibliography.FSTTCS_10_CITEKEY
        + "]. This translation has an _symbolic_ implementation and "
        + "is provided for testing purposes through this interface. In order to benefit from the "
        + "symbolic implementation users _must_ use the 'SymbolicAutomaton'-interface.";

    public static final String SLM21_DESCRIPTION = "Translate the formula to a "
        + "deterministic parity automaton by combining the LTL-to-DELA translation of ["
        + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "] with a DELW-to-DPW translation based on "
        + "Zielonka-trees. Depending on the lookahead either [" + Bibliography.ICALP_21_CITEKEY
        + "] "
        + " or [" + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "] is used.";

    public static final String SMALLEST_AUTOMATON_DESCRIPTION = "Run all available DPA-"
        + "translations with all optimisations turned on in parallel and return the smallest "
        + "automaton.";

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
    translation(Class<A> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case SEJK16_EKRS17 -> {
          var configuration = EnumSet.of(POST_PROCESS);

          if (translationOptions.contains(Option.X_DPA_USE_COMPLEMENT)) {
            configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
          }

          var ekrs17translation = new LTL2DPAFunction(configuration);

          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(ekrs17translation.apply(x), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);
        }

        case EKS20_EKRS17 -> {
          var configuration = EnumSet.of(SYMMETRIC, POST_PROCESS);

          if (translationOptions.contains(Option.X_DPA_USE_COMPLEMENT)) {
            configuration.add(COMPLEMENT_CONSTRUCTION_EXACT);
          }

          var ekrs17translation = new LTL2DPAFunction(configuration);

          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(ekrs17translation.apply(x), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);
        }

        case SYMBOLIC_SE20_BKS10 -> {
          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(
                  SymbolicDPAConstruction.of().apply(x).toAutomaton(), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);
        }

        case SLM21 -> {
          var translation = new NormalformDPAConstruction(lookahead);

          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(translation.apply(x), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass
          );
        }

        case SMALLEST_AUTOMATON -> {
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.X_DPA_USE_COMPLEMENT);
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

            Supplier<Optional<Automaton<?, ? extends A>>> se20bks10SymbolicTranslation =
                () -> Optional.of(
                    SYMBOLIC_SE20_BKS10.translation(acceptanceClass, copiedTranslationOptions)
                        .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends A>>> slm21translation = () -> Optional.of(
                SLM21.translation(acceptanceClass, copiedTranslationOptions, OptionalInt.empty())
                    .apply(labelledFormula));

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
                List.of(
                    () -> portfolioTranslation.apply(labelledFormula),
                    sejk16translation,
                    eks20translation,
                    se20bks10SymbolicTranslation,
                    slm21translation
                )));
          };
        }

        default -> throw new AssertionError();
      }
    }

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    @Nullable
    public static native LtlToDpaTranslation fromCValue(int value);
  }

  @CEnum("owl_ltl_to_dra_translation")
  public enum LtlToDraTranslation
      implements LtlTranslation<RabinAcceptance, GeneralizedRabinAcceptance> {

    @CEnumConstant("OWL_LTL_TO_DRA_EKS16")
    EKS16(Bibliography.FMSD_16_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DRA_EKS20")
    EKS20(Bibliography.JACM_20_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DRA_SE20")
    SE20(Bibliography.LICS_20_CITEKEY),
    @CEnumConstant("OWL_LTL_TO_DRA_SMALLEST_AUTOMATON")
    SMALLEST_AUTOMATON(null);

    public static final LtlToDraTranslation DEFAULT = EKS20;

    public static final String EKS16_DESCRIPTION = "Translate the formula to a deterministic "
        + "(generalised) Rabin automaton by guessing and checking the set of greatest fixed-point "
        + "operators, i.e. G, R, and W, that is satisfied by almost all suffixes of the word read by "
        + "the automaton. This construction is also known as the original 'Rabinizer'-construction ["
        + Bibliography.FMSD_16_CITEKEY + "] and used to be available through the '--asymmetric' "
        + "option.";

    public static final String EKS20_DESCRIPTION = "Translate the formula to a deterministic "
        + "(generalised) Rabin automaton by guessing and checking the set of greatest fixed-point "
        + "operators, i.e. G, R, and W, that is satisfied by almost all suffixes of the word read by "
        + "the automaton and the set of least fixed-point operators, i.e. F, M, and U, that is "
        + "satisfied by infinitely many suffixes of the word read by the automaton. This construction"
        + " has been initially proposed in [" + Bibliography.LICS_18_CITEKEY + "] and has been "
        + "described in more detail and with optimisations in ["
        + Bibliography.DISSERTATION_19_CITEKEY
        + "]. The preferred reference to cite is the journal "
        + "article [" + Bibliography.JACM_20_CITEKEY + "]. The translation used to be available "
        + "through the '--symmetric' option.";

    public static final String SE20_DESCRIPTION = "Translate the formula to a deterministic "
        + "(generalised) Rabin automaton by rewriting the formula into the Δ₂-normalform using the "
        + "procedure of [" + Bibliography.LICS_20_CITEKEY + "] and then by using the constructions "
        + "for 'simple' LTL fragments from [" + Bibliography.LICS_20_CITEKEY + ", "
        + Bibliography.DISSERTATION_19_CITEKEY + "].";

    public static final String SMALLEST_AUTOMATON_DESCRIPTION = "Run all available "
        + "D(G)RA-translations with all optimisations turned on in parallel and return the smallest "
        + "automaton.";

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
    translation(Class<R> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead) {

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
                    RabinDegeneralization.degeneralize(AcceptanceOptimizations.transform(dgra)),
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
                  translationOptions.contains(Option.X_DRA_NORMAL_FORM_USE_DUAL)),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);

        case SMALLEST_AUTOMATON:
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.X_DRA_NORMAL_FORM_USE_DUAL);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);

          var portfolioTranslation = portfolioWithPreAndPostProcessing(
              BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);

          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends R>>> sejk16translation = () -> Optional.of(
                EKS16.translation(acceptanceClass, copiedTranslationOptions)
                    .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends R>>> eks20translation = () -> Optional.of(
                EKS20.translation(acceptanceClass, copiedTranslationOptions)
                    .apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends R>>> se20translation = () -> Optional.of(
                SE20.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            return ParallelEvaluation.takeSmallestWildcardStateType(ParallelEvaluation.evaluate(
                List.of(
                    () -> portfolioTranslation.apply(labelledFormula),
                    sejk16translation,
                    eks20translation,
                    se20translation
                )));
          };

        default:
          throw new AssertionError("Unreachable.");
      }
    }

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    @Nullable
    public static native LtlToDraTranslation fromCValue(int value);
  }

  public enum LtlToDelaTranslation
      implements LtlTranslation<EmersonLeiAcceptance, EmersonLeiAcceptance> {

    MS17(Bibliography.GANDALF_17_CITEKEY),
    SLM21(Bibliography.UNDER_SUBMISSION_21_CITEKEY),
    SMALLEST_AUTOMATON(null);

    public static final LtlToDelaTranslation DEFAULT = SLM21;

    public static final String MS17_DESCRIPTION = "Translate the formula to a deterministic "
        + "Emerson-Lei automaton using an specialised product construction and a portfolio of "
        + "constructions for simple LTL fragments. This construction has been originally be "
        + "implemented in 'delag' and presented in [" + Bibliography.GANDALF_17_CITEKEY + "]. If a "
        + "subformula is not in a supported fragment then [" + Bibliography.JACM_20_CITEKEY
        + "] is "
        + "used as a fallback.";

    public static final String SLM21_DESCRIPTION = "Translate the formula to a deterministic "
        + "Emerson-Lei automaton by rewriting the formula locally into the Δ₂-normalform using the "
        + "procedure of [" + Bibliography.LICS_20_CITEKEY
        + "], i.e., only temporal subformulas that "
        + "are not in Δ₂ are rewritten, and then use an specialised product construction ["
        + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "] to obtain a deterministic automaton. After "
        + "rewriting each temporal subformula is in Σ₂ or Π₂ and the direct translation to "
        + "deterministic co-Büchi and Büchi automata from ["
        + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "] is sufficient.";

    public static final String SMALLEST_AUTOMATON_DESCRIPTION = "Run all available DELA- and DGRA-"
        + "translations with all optimisations turned on in parallel and return the smallest "
        + "automaton.";

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
    translation(Class<A> acceptanceClass, Set<Option> translationOptions, OptionalInt lookahead) {

      Preconditions.checkArgument(acceptanceClassWithinBounds(acceptanceClass));

      switch (this) {
        case MS17 -> {
          var ms17construction = new DelagBuilder(
              LtlToDraTranslation.EKS20.translation(GeneralizedRabinAcceptance.class));
          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(ms17construction.apply(x), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);
        }

        case SLM21 -> {
          var slm21Construction = new NormalformDELAConstruction();
          return applyPreAndPostProcessing(
              x -> OmegaAcceptanceCast.cast(slm21Construction.apply(x), acceptanceClass),
              BranchingMode.DETERMINISTIC,
              translationOptions,
              acceptanceClass);
        }

        case SMALLEST_AUTOMATON -> {
          var copiedTranslationOptions = EnumSet.copyOf(translationOptions);
          copiedTranslationOptions.add(Option.X_DRA_NORMAL_FORM_USE_DUAL);
          copiedTranslationOptions.remove(Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);
          var portfolioTranslation = portfolioWithPreAndPostProcessing(
              BranchingMode.DETERMINISTIC, translationOptions, acceptanceClass);
          return labelledFormula -> {
            Supplier<Optional<Automaton<?, ? extends A>>> ms17translation = () -> Optional.of(
                MS17.translation(acceptanceClass, copiedTranslationOptions).apply(labelledFormula));

            Supplier<Optional<Automaton<?, ? extends A>>> slm21translation = () -> Optional.of(
                SLM21.translation(acceptanceClass, copiedTranslationOptions)
                    .apply(labelledFormula));

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
                    slm21translation,
                    dgraTranslation
                )));
          };
        }

        default -> throw new AssertionError();
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
    boolean completeAutomaton = translationOptions.contains(Option.COMPLETE);

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
          ? SimplifierRepository.SYNTACTIC_FIXPOINT.apply(unprocessedFormula)
          : unprocessedFormula;
      var automaton = simplifyAutomaton
          ? AcceptanceOptimizations.transform(wrappedFunction.apply(formula))
          : wrappedFunction.apply(formula);

      if (completeAutomaton) {
        if (automaton.acceptance() instanceof AllAcceptance
            && acceptanceCondition.equals(GeneralizedBuchiAcceptance.class)) {

          return OmegaAcceptanceCast.cast(
              Views.complete(OmegaAcceptanceCast.cast(automaton, BuchiAcceptance.class)),
              acceptanceCondition);
        } else {
          return OmegaAcceptanceCast.cast(Views.complete(automaton), acceptanceCondition);
        }
      }

      return automaton;
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
    boolean completeAutomaton = translationOptions.contains(Option.COMPLETE);

    var portfolio = branchingMode == BranchingMode.NON_DETERMINISTIC
        ? new NonDeterministicConstructionsPortfolio<>(acceptanceCondition)
        : new DeterministicConstructionsPortfolio<>(acceptanceCondition);

    return unprocessedFormula -> {
      var formula = simplifyFormula
          ? SimplifierRepository.SYNTACTIC_FIXPOINT.apply(unprocessedFormula)
          : unprocessedFormula;
      var automatonOptional = portfolio.apply(formula);

      if (automatonOptional.isEmpty()) {
        return Optional.empty();
      }

      var automaton = simplifyAutomaton
          ? AcceptanceOptimizations.transform(automatonOptional.get())
          : automatonOptional.get();

      if (completeAutomaton) {
        if (automaton.acceptance() instanceof AllAcceptance
            && acceptanceCondition.equals(GeneralizedBuchiAcceptance.class)) {

          return Optional.of(OmegaAcceptanceCast.cast(
              Views.complete(OmegaAcceptanceCast.cast(automaton, BuchiAcceptance.class)),
              acceptanceCondition));
        } else {
          return Optional.of(
              OmegaAcceptanceCast.cast(Views.complete(automaton), acceptanceCondition));
        }
      }

      return Optional.of(automaton);
    };
  }
}
