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

package owl.command;

import static owl.command.Mixins.AcceptanceSimplifier;
import static owl.command.Mixins.FormulaReader;
import static owl.command.Mixins.FormulaSimplifier;
import static owl.translations.LtlTranslationRepository.LtlToDelaTranslation;
import static owl.translations.LtlTranslationRepository.LtlToDpaTranslation;
import static owl.translations.LtlTranslationRepository.LtlToDraTranslation;
import static owl.translations.LtlTranslationRepository.Option.COMPLETE;
import static owl.translations.LtlTranslationRepository.Option.SIMPLIFY_AUTOMATON;
import static owl.translations.LtlTranslationRepository.Option.SIMPLIFY_FORMULA;
import static owl.translations.LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS;
import static owl.translations.LtlTranslationRepository.Option.X_DPA_USE_COMPLEMENT;
import static owl.translations.LtlTranslationRepository.Option.X_DRA_NORMAL_FORM_USE_DUAL;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.OptionalInt;
import java.util.Set;
import owl.Bibliography;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedBuchiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.ParityAcceptance;
import owl.automaton.acceptance.RabinAcceptance;
import owl.command.Mixins.AutomatonWriter;
import owl.ltl.LabelledFormula;
import owl.thirdparty.picocli.CommandLine;
import owl.thirdparty.picocli.CommandLine.Command;
import owl.thirdparty.picocli.CommandLine.Mixin;
import owl.thirdparty.picocli.CommandLine.Option;
import owl.translations.LtlTranslationRepository;
import owl.translations.LtlTranslationRepository.LtlToLdbaTranslation;
import owl.translations.LtlTranslationRepository.LtlToNbaTranslation;

@SuppressWarnings("PMD.ImmutableField")
final class LtlTranslationCommands {

  private LtlTranslationCommands() {
  }

  private abstract static class AbstractLtl2AutomatonCommand
      <L extends A, A extends EmersonLeiAcceptance> extends AbstractOwlSubcommand {

    protected static final String LIST_AVAILABLE_TRANSLATIONS = "The default translation is "
        + "${DEFAULT-VALUE} and the following translations are available: ${COMPLETION-CANDIDATES}.";

    @Mixin
    private FormulaReader formulaReader = null;

    @Mixin
    private AutomatonWriter automatonWriter = null;

    @Mixin
    private FormulaSimplifier formulaSimplifier = null;

    @Mixin
    private AcceptanceSimplifier acceptanceSimplifier = null;

    @Option(
        names = "--skip-translation-portfolio",
        description = "Bypass the portfolio of constructions from [S19, SE20] that directly "
            + "translates 'simple' fragments of LTL to automata."
    )
    private boolean skipPortfolio = false;

    @Override
    protected int run() throws Exception {
      var translation = translation();
      var acceptanceClass = acceptanceClass();

      var basicOptions = EnumSet.noneOf(LtlTranslationRepository.Option.class);

      if (!formulaSimplifier.skipSimplifier) {
        basicOptions.add(SIMPLIFY_FORMULA);
      }

      if (!acceptanceSimplifier.skipAcceptanceSimplifier) {
        basicOptions.add(SIMPLIFY_AUTOMATON);
      }

      if (automatonWriter.complete) {
        basicOptions.add(COMPLETE);
      }

      if (!skipPortfolio) {
        basicOptions.add(USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS);
      }

      basicOptions.addAll(extraOptions());

      var subcommand = getClass().getAnnotation(Command.class).name();
      var translator = translation.translation(acceptanceClass, basicOptions, lookahead());

      try (var source = formulaReader.source();
          var sink = automatonWriter.sink(subcommand, rawArgs())) {

        Iterator<LabelledFormula> formulaIterator = source.iterator();

        while (formulaIterator.hasNext()) {
          LabelledFormula formula = formulaIterator.next();
          sink.accept(translator.apply(formula), "Automaton for " + formula);
        }
      }

      return 0;
    }

    protected abstract LtlTranslationRepository.LtlTranslation<L, A> translation();

    protected abstract Class<? extends A> acceptanceClass();

    protected Set<LtlTranslationRepository.Option> extraOptions() {
      return Set.of();
    }

    protected OptionalInt lookahead() {
      return OptionalInt.empty();
    }
  }

  private abstract static class AbstractLtl2NbaCommand
      extends AbstractLtl2AutomatonCommand<BuchiAcceptance, GeneralizedBuchiAcceptance> {

    @Option(
        names = {"-t", "--translation"},
        description = {
            LIST_AVAILABLE_TRANSLATIONS,
            "EKS20: " + LtlToNbaTranslation.EKS20_DESCRIPTION
        },
        defaultValue = "EKS20",
        showDefaultValue = CommandLine.Help.Visibility.NEVER
    )
    private LtlToNbaTranslation translation = LtlToNbaTranslation.DEFAULT;

    @Override
    protected final LtlToNbaTranslation translation() {
      return translation;
    }
  }

  @Command(
      name = "ltl2nba",
      description = {
          "Translate a linear temporal logic (LTL) formula into a non-deterministic Büchi automaton "
              + "(NBA).",
          "Usage Examples:",
          "  owl ltl2nba -f 'F (a & G b)'",
          "  owl ltl2nba -t EKS20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2NbaCommand extends AbstractLtl2NbaCommand {

    @Override
    protected Class<? extends GeneralizedBuchiAcceptance> acceptanceClass() {
      return BuchiAcceptance.class;
    }
  }

  @Command(
      name = "ltl2ngba",
      description = {
          "Translate a linear temporal logic (LTL) formula into a non-deterministic generalized "
              + "Büchi automaton (NGBA).",
          "Usage Examples:",
          "  owl ltl2ngba -f 'F (a & G b)'",
          "  owl ltl2ngba -t EKS20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2NgbaCommand extends AbstractLtl2NbaCommand {

    @Override
    protected Class<? extends GeneralizedBuchiAcceptance> acceptanceClass() {
      return GeneralizedBuchiAcceptance.class;
    }
  }

  private abstract static class AbstractLtl2LdbaCommand
      extends AbstractLtl2AutomatonCommand<BuchiAcceptance, GeneralizedBuchiAcceptance> {

    @Option(
        names = {"-t", "--translation"},
        description = {
            LIST_AVAILABLE_TRANSLATIONS,
            "SEJK16: " + LtlToLdbaTranslation.SEJK16_DESCRIPTION,
            "EKS20: " + LtlToLdbaTranslation.EKS20_DESCRIPTION,
            "SMALLEST_AUTOMATON: " + LtlToLdbaTranslation.SMALLEST_AUTOMATON_DESCRIPTION
        },
        defaultValue = "SEJK16",
        showDefaultValue = CommandLine.Help.Visibility.NEVER
    )
    private LtlToLdbaTranslation translation = LtlToLdbaTranslation.DEFAULT;

    @Override
    protected final LtlToLdbaTranslation translation() {
      return translation;
    }
  }

  @Command(
      name = "ltl2ldba",
      description = {
          "Translate a linear temporal logic (LTL) formula into a limit-deterministic Büchi automaton "
              + "(LDBA).",
          "Usage Examples:",
          "  owl ltl2ldba -f 'F (a & G b)'",
          "  owl ltl2ldba -t EKS20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2LdbaCommand extends AbstractLtl2LdbaCommand {

    @Override
    protected Class<? extends GeneralizedBuchiAcceptance> acceptanceClass() {
      return BuchiAcceptance.class;
    }
  }

  @Command(
      name = "ltl2ldgba",
      description = {
          "Translate a linear temporal logic (LTL) formula into a limit-deterministic generalized "
              + "Büchi automaton (LDGBA).",
          "Usage Examples:",
          "  owl ltl2ldgba -f 'F (a & G b)'",
          "  owl ltl2ldgba -t EKS20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2LdgbaCommand extends AbstractLtl2LdbaCommand {

    @Override
    protected Class<? extends GeneralizedBuchiAcceptance> acceptanceClass() {
      return GeneralizedBuchiAcceptance.class;
    }
  }

  @Command(
      name = "ltl2dpa",
      description = {
          "Translate a linear temporal logic (LTL) formula into a deterministic parity automaton "
              + "(DPA).",
          "Usage Examples:",
          "  owl ltl2dpa -f 'F (a & G b)'",
          "  owl ltl2dpa -t SEJK16_EKRS17 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2DpaCommand
      extends AbstractLtl2AutomatonCommand<ParityAcceptance, ParityAcceptance> {

    @Option(
        names = {"-t", "--translation"},
        description = {
            LIST_AVAILABLE_TRANSLATIONS,
            "SEJK16_EKRS17: " + LtlToDpaTranslation.SEJK16_EKRS17_DESCRIPTION,
            "EKS20_EKRS17: " + LtlToDpaTranslation.EKS20_EKRS17_DESCRIPTION,
            "SYMBOLIC_SE20_BKS10: " + LtlToDpaTranslation.SYMBOLIC_SE20_BKS10_DESCRIPTION,
            "SLM21: " + LtlToDpaTranslation.SLM21_DESCRIPTION,
            "SMALLEST_AUTOMATON: " + LtlToDpaTranslation.SMALLEST_AUTOMATON_DESCRIPTION
        },
        defaultValue = "SLM21",
        showDefaultValue = CommandLine.Help.Visibility.NEVER
    )
    private LtlToDpaTranslation translation = LtlToDpaTranslation.DEFAULT;

    @Option(
        names = "--" + Bibliography.TACAS_17_1_CITEKEY + "-skip-complement",
        description =
            "Bypass the parallel computation of a DPA for the negation of the formula. If "
                + "the parallel computation is enabled, then two DPAs are computed and the "
                + "smaller one (in terms of number of states) is returned."
    )
    private boolean skipComplement = false;

    @Option(
        names = {"--" + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "-lookahead"},
        description = {
            "The number of successor states that are explored in order to compute "
                + "the 'Alternating Cycle Decomposition' [" + Bibliography.ICALP_21_CITEKEY
                + "]. If the "
                + "number of explored states exceeds this bound, a sound approximations are used as "
                + "desribed in [" + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "]. If the "
                + "value is 0, only approximations are used. If the value is negative, then all "
                + "states are explored and exact semantic information is used. The value is by default "
                + "${DEFAULT-VALUE}. If the construction times out, try setting this value to 0 and then "
                + "increase it again in order to obtain smaller automata. This option only affects the "
                + Bibliography.UNDER_SUBMISSION_21_CITEKEY + "-translation."
        },
        defaultValue = "-1"
    )
    private int lookahead = -1;

    @Override
    protected LtlToDpaTranslation translation() {
      return translation;
    }

    @Override
    protected Class<? extends ParityAcceptance> acceptanceClass() {
      return ParityAcceptance.class;
    }

    @Override
    protected Set<LtlTranslationRepository.Option> extraOptions() {
      return skipComplement ? Set.of() : Set.of(X_DPA_USE_COMPLEMENT);
    }

    @Override
    protected OptionalInt lookahead() {
      return lookahead < 0 ? OptionalInt.empty() : OptionalInt.of(lookahead);
    }
  }

  private abstract static class AbstractLtl2DraCommand
      extends AbstractLtl2AutomatonCommand<RabinAcceptance, GeneralizedRabinAcceptance> {

    @Option(
        names = {"-t", "--translation"},
        description = {
            LIST_AVAILABLE_TRANSLATIONS,
            "EKS16: " + LtlToDraTranslation.EKS16_DESCRIPTION,
            "EKS20: " + LtlToDraTranslation.EKS20_DESCRIPTION,
            "SE20: " + LtlToDraTranslation.SE20_DESCRIPTION,
            "SMALLEST_AUTOMATON: " + LtlToDraTranslation.SMALLEST_AUTOMATON_DESCRIPTION
        },
        defaultValue = "EKS20",
        showDefaultValue = CommandLine.Help.Visibility.NEVER
    )
    private LtlToDraTranslation translation = LtlToDraTranslation.DEFAULT;

    @Option(
        names = {"--" + Bibliography.LICS_20_CITEKEY + "-no-dual-normal-form"},
        description = "Do not use the dual normal form of [SE20] for the translation to "
            + "deterministic automata. This option is only honoured if SE20 is selected as translation."
    )
    private boolean noSe20dual = true;

    @Override
    protected final LtlToDraTranslation translation() {
      return translation;
    }

    @Override
    protected final Set<LtlTranslationRepository.Option> extraOptions() {
      return noSe20dual ? Set.of() : Set.of(X_DRA_NORMAL_FORM_USE_DUAL);
    }
  }

  @Command(
      name = "ltl2dra",
      description = {
          "Translate a linear temporal logic (LTL) formula into a deterministic Rabin automaton (DRA).",
          "Usage Examples:",
          "  owl ltl2dra -f 'F (a & G b)'",
          "  owl ltl2dra -t SE20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2DraCommand extends AbstractLtl2DraCommand {

    @Override
    protected Class<? extends GeneralizedRabinAcceptance> acceptanceClass() {
      return RabinAcceptance.class;
    }
  }

  @Command(
      name = "ltl2dgra",
      description = {
          "Translate a linear temporal logic (LTL) formula into a deterministic generalized Rabin "
              + "automaton (DGRA).",
          "Usage Examples:",
          "  owl ltl2dgra -f 'F (a & G b)'",
          "  owl ltl2dgra -t SE20 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2DgraCommand extends AbstractLtl2DraCommand {

    @Override
    protected Class<? extends GeneralizedRabinAcceptance> acceptanceClass() {
      return GeneralizedRabinAcceptance.class;
    }
  }

  @Command(
      name = "ltl2dela",
      description = {
          "Translate a linear temporal logic (LTL) formula into a deterministic Emerson-Lei "
              + "automaton (DELA).",
          "Usage Examples:",
          "  owl ltl2dela -f 'F (a & G b)'",
          "  owl ltl2dela -t MS17 -i input-file -o output-file",
          MiscCommands.BibliographyCommand.HOW_TO_USE
      }
  )
  static final class Ltl2DelaCommand
      extends AbstractLtl2AutomatonCommand<EmersonLeiAcceptance, EmersonLeiAcceptance> {

    @Option(
        names = {"-t", "--translation"},
        description = {
            LIST_AVAILABLE_TRANSLATIONS,
            "MS17: " + LtlToDelaTranslation.MS17_DESCRIPTION,
            "SLM21: " + LtlToDelaTranslation.SLM21_DESCRIPTION,
            "SMALLEST_AUTOMATON: " + LtlToDelaTranslation.SMALLEST_AUTOMATON_DESCRIPTION
        },
        defaultValue = "SLM21",
        showDefaultValue = CommandLine.Help.Visibility.NEVER
    )
    private LtlToDelaTranslation translation = LtlToDelaTranslation.DEFAULT;

    @Override
    protected LtlToDelaTranslation translation() {
      return translation;
    }

    @Override
    protected Class<? extends EmersonLeiAcceptance> acceptanceClass() {
      return EmersonLeiAcceptance.class;
    }

    @Override
    protected EnumSet<LtlTranslationRepository.Option> extraOptions() {
      return EnumSet.noneOf(LtlTranslationRepository.Option.class);
    }
  }
}
