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

package owl.command;

import static owl.thirdparty.picocli.CommandLine.Command;
import static owl.thirdparty.picocli.CommandLine.Mixin;
import static owl.thirdparty.picocli.CommandLine.Option;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumSet;
import java.util.Iterator;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import owl.Bibliography;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.SyntacticFragments;
import owl.ltl.rewriter.SimplifierRepository;
import owl.ltl.robust.RobustLtlParser;
import owl.ltl.robust.Robustness;
import owl.translations.mastertheorem.Normalisation;
import owl.translations.mastertheorem.Normalisation.NormalisationMethod;

@SuppressWarnings("PMD.ImmutableField")
final class LtlConversionCommands {

  private LtlConversionCommands() {}

  @Command(
    name = "ltl-utilities",
    description = "A collection of various linear temporal logic related rewriters."
  )
  static final class LtlUtilities extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.FormulaReader formulaReader = null;

    @Mixin
    private Mixins.FormulaWriter formulaWriter = null;

    @Option(
      names = "--rewriter",
      description = "The rewriter to apply. Possible values are: ${COMPLETION-CANDIDATES}.",
      required = true
    )
    private SimplifierRepository mode = SimplifierRepository.SYNTACTIC_FIXPOINT;

    @Override
    protected int run() throws IOException {
      try (var source = formulaReader.source();
           var sink = formulaWriter.sink()) {

        source.map(mode::apply).forEachOrdered(formula -> {
          try {
            sink.accept(formula);
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
        });
      }

      return 0;
    }
  }

  @Command(
    name = "ltl2delta2",
    description = {
      "Rewrite a linear temporal logic (LTL) formula into the Δ₂-normal-form using the "
        + "construction of [" + Bibliography.LICS_20_CITEKEY + "].",
      "Usage Examples:",
      "  owl ltl2delta2 -f 'F (a & G (b | F c)) & G F d'",
      "  owl ltl2delta2 --method=SE20_PI_2_AND_FG_P1_1 -f 'F (a & G (b | F c)) & G F d'",
      MiscCommands.BibliographyCommand.HOW_TO_USE
    }
  )
  static final class Delta2Normalisation extends AbstractOwlSubcommand {

    @Option(
      names = {"--method"},
      description = {
        "Select the normalisation method from [" + Bibliography.LICS_20_CITEKEY + "] "
        + "that should be applied. The default method is ${DEFAULT-VALUE} and the following "
        + "methods are available: ${COMPLETION-CANDIDATES}.",
        "SE20_SIGMA_2_AND_GF_SIGMA_1: This method corresponds to [" + Bibliography.LICS_20_CITEKEY
          + ", Theorem 23].",
        "SE20_PI_2_AND_FG_P1_1: This method corresponds to [" + Bibliography.LICS_20_CITEKEY
          + ", Theorem 27].",
      },
      defaultValue = "SE20_SIGMA_2_AND_GF_SIGMA_1"
    )
    private NormalisationMethod method = NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1;

    @Option(
      names = "--strict",
      description = {
        "Ensure that the computed formula is not only in Δ₂, i.e., a Boolean combination of "
          + "formulas from Σ₂ and Π₂, but that depending on the selected normalisation method "
          + "(see '--method') the formula is a Boolean combination of temporal operators from Σ₂ "
          + "and GF(Σ₁), or Π₂ and FG(Π₁), respectively."
      }
    )
    private boolean strict = false;

    @Mixin
    private Mixins.FormulaReader formulaReader = null;

    @Mixin
    private Mixins.FormulaWriter formulaWriter = null;

    @Mixin
    private Mixins.FormulaSimplifier formulaSimplifier = null;

    @Mixin
    private Mixins.Verifier verify = null;

    @Override
    protected int run() throws IOException {

      try (var source = formulaReader.source();
           var sink = formulaWriter.sink()) {

        Normalisation normalisation = Normalisation.of(method, strict);
        Iterator<LabelledFormula> formulaIterator = source.iterator();

        while (formulaIterator.hasNext()) {
          var formula = formulaIterator.next();

          if (!formulaSimplifier.skipSimplifier) {
            formula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(formula);
          }

          formula = normalisation.apply(formula);

          // TODO: Simplifier sometimes destroys normal form.
          // if (!formulaSimplifier.skipSimplifier) {
          //  formula = SimplifierRepository.SYNTACTIC_FIXPOINT.apply(formula);
          // }

          if (verify != null && verify.verify && !SyntacticFragments.DELTA_2.contains(formula)) {
            throw new AssertionError("Verification failed.");
          }

          if (verify != null && verify.verify && strict) {
            if (method == NormalisationMethod.SE20_SIGMA_2_AND_GF_SIGMA_1) {
              if (formula.formula().anyMatch(x -> x instanceof Formula.TemporalOperator
                && !Normalisation.isSigma2OrGfSigma1((Formula.TemporalOperator) x))) {

                throw new AssertionError(String.format("Verification failed for %s.", formula));
              }
            } else if (method == NormalisationMethod.SE20_PI_2_AND_FG_PI_1) {
              if (formula.formula().anyMatch(x -> x instanceof Formula.TemporalOperator
                && !Normalisation.isPi2OrFgPi1((Formula.TemporalOperator) x))) {

                throw new AssertionError(String.format("Verification failed for %s.", formula));
              }
            } else {
              throw new IllegalStateException("Unexpected value: " + method);
            }
          }

          sink.accept(formula);
        }
      }

      return 0;
    }
  }

  @Command(
    name = "rltl2ltl",
    description =
      "Convert a robust linear temporal logic (rLTL) formula into a linear temporal logic formula."
  )
  static final class RLtlReader extends AbstractOwlSubcommand {

    @Mixin
    private Mixins.FormulaReader formulaReader = null;

    @Mixin
    private Mixins.FormulaWriter formulaWriter = null;

    @Option(
      names = {"-t", "--truth"},
      description =
        "The truth value that has to be satisfied. Possible values: ${COMPLETION-CANDIDATES}",
      required = true
    )
    private Robustness robustness = null;

    @Override
    protected int run() throws IOException {

      try (var source = formulaReader.stringSource();
           var sink = formulaWriter.sink()) {

        Iterator<LabelledFormula> formulaIterator = source
          .map((String line) -> {
            try {
              return RobustLtlParser.parse(line).toLtl(EnumSet.of(robustness));
            } catch (RecognitionException | ParseCancellationException e) {
              throw new IllegalArgumentException("Failed to parse LTL formula " + line, e);
            }
          }).iterator();

        while (formulaIterator.hasNext()) {
          sink.accept(formulaIterator.next());
        }
      }

      return 0;
    }
  }
}
