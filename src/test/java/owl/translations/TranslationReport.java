/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

import static owl.translations.TranslationAutomatonSummaryTest.AutomatonSummary;
import static owl.translations.TranslationAutomatonSummaryTest.COMMON_ALPHABET;
import static owl.translations.TranslationAutomatonSummaryTest.FormulaSet;
import static owl.translations.TranslationAutomatonSummaryTest.Translator;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Table;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BuchiAcceptance;
import owl.automaton.acceptance.EmersonLeiAcceptance;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptanceCast;
import owl.automaton.acceptance.RabinAcceptance;
import owl.automaton.acceptance.degeneralization.RabinDegeneralization;
import owl.automaton.acceptance.optimization.AcceptanceOptimizations;
import owl.automaton.acceptance.transformer.ZielonkaTreeTransformations;
import owl.collections.Collections3;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.LatexPrintVisitor;
import owl.translations.ExternalTranslator.InputMode;
import owl.translations.ltl2dra.NormalformDRAConstruction;
import owl.translations.ltl2dra.SymmetricDRAConstruction;
import owl.translations.rabinizer.RabinizerBuilder;
import owl.translations.rabinizer.RabinizerConfiguration;
import owl.util.Statistics;

@SuppressWarnings("PMD")
class TranslationReport {
  private static final LatexPrintVisitor PRINT_VISITOR = new LatexPrintVisitor(COMMON_ALPHABET);

  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"})
  void generateLics20Comparison(FormulaSet set) throws IOException {
    Function<LabelledFormula, Automaton<?, ?>> draSymmetric = (LabelledFormula formula) ->
      AcceptanceOptimizations.transform(
        SymmetricDRAConstruction.of(RabinAcceptance.class, true).apply(formula));

    var normalform =
      NormalformDRAConstruction.of(RabinAcceptance.class, false);

    int smaller = 0;
    int SMALLER = 0;
    int equal = 0;
    int EQUAL = 0;
    int larger = 0;
    int LARGER = 0;
    int completeCount = 0;

    Set<LabelledFormula> seenFormulas = new HashSet<>();
    List<Integer> draSizes = new ArrayList<>();
    List<Integer> normalformSizes = new ArrayList<>();

    for (LabelledFormula formula : set.loadAndDeduplicateFormulaSet()) {
      if (seenFormulas.contains(formula) || seenFormulas.contains(formula.not())) {
        continue;
      }

      completeCount = completeCount + 2;

      int draSize = draSymmetric.apply(formula).states().size();
      int normalformSize = normalform.apply(formula).states().size();

      draSizes.add(draSize);
      normalformSizes.add(normalformSize);

      if (draSize > normalformSize) {
        smaller++;
        System.out.println("d: " + draSize + " n: " + normalformSize + " f: " + formula);
      } else if (draSize < normalformSize) {
        larger++;
        System.out.println("d: " + draSize + " n: " + normalformSize + " f: " + formula);
      } else {
        equal++;
      }


      draSize = draSymmetric.apply(formula.not()).states().size();
      normalformSize = normalform.apply(formula.not()).states().size();

      draSizes.add(draSize);
      normalformSizes.add(normalformSize);

      if (draSize > normalformSize) {
        smaller++;
        System.out.println("d: " + draSize + " n: " + normalformSize + " f: " + formula.not());
      } else if (draSize < normalformSize) {
        larger++;
        System.out.println("d: " + draSize + " n: " + normalformSize + " f: " + formula.not());
      } else {
        equal++;
      }


      seenFormulas.add(formula);
      seenFormulas.add(formula.not());
    }

    System.out.println("geom dra:" + owl.util.Statistics.geometricMean(draSizes.stream().mapToInt(x -> x).toArray()));
    System.out.println("geom norm:" + owl.util.Statistics.geometricMean(normalformSizes.stream().mapToInt(x -> x).toArray()));

    List<Integer> differences = new ArrayList<>();
    List<Double> percentages = new ArrayList<>();

    for (int i = 0; i < draSizes.size(); i++) {
      int dra = draSizes.get(i);
      int norm = normalformSizes.get(i);

      differences.add(Math.abs(dra - norm));

      if (dra < norm) {
        percentages.add(((double) norm / (double) dra) - 1.0d);
      } else {
        percentages.add(((double) dra / (double) norm) - 1.0d);
      }
    }

    differences.sort(Comparator.<Integer>naturalOrder().reversed());
    System.out.println(differences);

    percentages.sort(Comparator.<Double>naturalOrder().reversed());
    System.out.println(percentages);

    System.out.println("s: " + smaller + " e: " + equal + " l: " + larger + " / c: " + completeCount);
  }

  @Disabled
  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"})
  void generateJacmComparison(FormulaSet set) throws IOException {
    Function<LabelledFormula, Automaton<?, ?>> dgraAsymmetric = (LabelledFormula formula) ->
      AcceptanceOptimizations.transform(
        RabinizerBuilder.build(formula, RabinizerConfiguration.of(true, true, true)));

    Function<LabelledFormula, Automaton<?, ?>> dgraSymmetric = (LabelledFormula formula) ->
      AcceptanceOptimizations.transform(
        SymmetricDRAConstruction.of(GeneralizedRabinAcceptance.class, true)
          .apply(formula));

    int symmeticLeq = 0;
    int completeCount = 0;

    Set<LabelledFormula> seenFormulas = new HashSet<>();

    for (LabelledFormula formula : set.loadAndDeduplicateFormulaSet()) {
      if (seenFormulas.contains(formula) || seenFormulas.contains(formula.not())) {
        continue;
      }

      completeCount = completeCount + 2;

      if (dgraSymmetric.apply(formula).states().size() <= dgraAsymmetric.apply(formula).states()
          .size()) {
        symmeticLeq++;
      }

      if (dgraSymmetric.apply(formula.not()).states().size() <= dgraAsymmetric.apply(formula.not())
          .states().size()) {
        symmeticLeq++;
      }

      seenFormulas.add(formula);
      seenFormulas.add(formula.not());
    }


    System.out.println(symmeticLeq + "/" + completeCount);
  }

  @Disabled
  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"})
  void generateJacmLatexTables(FormulaSet set) throws IOException {
    var ltl2dstarCommand = List.of(
      "./ltldo",
      "-T", "600",
      "-f", "%f",
      "-t", "./ltl2dstar --ltl2nba=spin:./ltl2ba --output-format=hoa %[MW]L %O");

    var ltl2tgbaCommand = List.of(
      "./ltldo",
      "-T", "600",
      "-f", "%f",
      "./ltl2tgba --generic --deterministic %f > %O"
    );

    var dgraAsymmetric = new Translator("DGRA (asymmetric)", (formula) ->
      AcceptanceOptimizations.transform(
        RabinizerBuilder.build(formula,
          RabinizerConfiguration.of(true, true, true))));

    var dgraSymmetric = new Translator("DGRA (this paper)", formula ->
      AcceptanceOptimizations.transform(
        SymmetricDRAConstruction.of(GeneralizedRabinAcceptance.class, true)
          .apply(formula)));

    var historic = List.of(new Translator("ltl2dstar (historic)",
      new ExternalTranslator(ltl2dstarCommand, InputMode.REPLACE)));

    var portfolio = List.of(new Translator("ltl2tgba (portfolio)",
      new ExternalTranslator(ltl2tgbaCommand, InputMode.REPLACE)));

    var direct = List.of(dgraAsymmetric, dgraSymmetric);
    var translators = List.of(historic, direct, portfolio);
    var formulaSet = set.loadAndDeduplicateFormulaSet();

    Table<Formula, String, AutomatonSummary> resultTable = HashBasedTable.create();

    for (List<Translator> group : translators) {
      for (Translator translator : group) {
        var translatorFunction = translator.constructor;

        for (LabelledFormula formula : formulaSet) {
          var summary = AutomatonSummary.of(() -> translatorFunction.apply(formula));
          var negationSummary = AutomatonSummary.of(() -> translatorFunction.apply(formula.not()));

          if (summary != null) {
            resultTable.put(formula.formula(), translator.name, summary);
          }

          if (negationSummary != null) {
            resultTable.put(formula.formula().not(), translator.name, negationSummary);
          }
        }
      }
    }

    var latexReport = LatexReport.create(
      translators.stream()
        .map(x -> Collections3.transformList(x, y -> y.name))
        .toList(),
      Map.of(),
      set.name().toLowerCase(),
      formulaSet.stream().map(LabelledFormula::formula).toList(),
      resultTable,
      z -> -1, 25, true);

    try (Writer writer = Files.newBufferedWriter(
      Paths.get(set.name() + ".tex"), StandardCharsets.UTF_8)) {
      writer.write(latexReport);
    }
  }

  @Disabled
  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"})
  void generateStttLatexReport(FormulaSet set) throws IOException {
    var configuration = RabinizerConfiguration.of(true, true, true);

    // Without Portfolio.

    var dpa_ldba_asymmetric = new Translator("\\LDone",
        LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
            EnumSet.noneOf(LtlTranslationRepository.Option.class)));

    var dpa_ldba_symmetric = new Translator("\\LDtwo",
        LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
            EnumSet.noneOf(LtlTranslationRepository.Option.class)));

    var dpa_iar_asymmetric = new Translator("\\Done", formula -> {
      var dgra = RabinizerBuilder.build(formula, configuration);
      var optimisedDgra = OmegaAcceptanceCast.cast(
        AcceptanceOptimizations.transform(dgra), GeneralizedRabinAcceptance.class);
      // Upgraded DGRA -> DPA translation. This was not done in original report.
      return ZielonkaTreeTransformations.transform(optimisedDgra);
    });

    var dpa_iar_symmetric = new Translator("\\Dtwo", formula -> {
      var dgra = SymmetricDRAConstruction.of(
        GeneralizedRabinAcceptance.class, true).apply(formula);
      var optimisedDgra = OmegaAcceptanceCast.cast(
        AcceptanceOptimizations.transform(dgra), GeneralizedRabinAcceptance.class);
      // Upgraded DGRA -> DPA translation. This was not done in original report.
      return ZielonkaTreeTransformations.transform(optimisedDgra);
    });

    // With Portfolio

    var dpa_ldba_asymmetric_portfolio = new Translator("\\LDp", labelledFormula -> {

      var automaton1
        = LtlTranslationRepository.LtlToDpaTranslation.SEJK16_EKRS17.translation(
          EnumSet.of(LtlTranslationRepository.Option.X_DPA_USE_COMPLEMENT,
              LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)).apply(labelledFormula);

      var automaton2
        = LtlTranslationRepository.LtlToDpaTranslation.EKS20_EKRS17.translation(
          EnumSet.of(LtlTranslationRepository.Option.X_DPA_USE_COMPLEMENT,
              LtlTranslationRepository.Option.USE_PORTFOLIO_FOR_SYNTACTIC_LTL_FRAGMENTS)).apply(labelledFormula);
      return automaton1.states().size() <= automaton2.states().size() ? automaton1 : automaton2;
    });

    // External

    var ltl2tgbaCommand = List.of(
      "ltldo",
      "-T", "600",
      "-f", "%f",
      "ltl2tgba --parity --deterministic %f > %O"
    );

    var nbaDetCommand = List.of(
      "ltldo",
      "-T", "600",
      "-f", "%f",
      "ltl2tgba -B %f | "
        + "/sttt-experiments/tools/nbautils/build/bin/nbadet -k -j -t -i -r -o -m -d -u2 > %O"
    );

    var ltl2tgba = new Translator("\\None",
      new ExternalTranslator(ltl2tgbaCommand, InputMode.REPLACE));

    var nbaDet = new Translator("\\Ntwo",
      new ExternalTranslator(nbaDetCommand, InputMode.REPLACE));

    var translators = List.of(List.of(ltl2tgba, nbaDet,
      dpa_iar_asymmetric, dpa_iar_symmetric,
      dpa_ldba_asymmetric, dpa_ldba_symmetric, dpa_ldba_asymmetric_portfolio));
    var formulaSet = set.loadAndDeduplicateFormulaSet();

    Table<Formula, String, AutomatonSummary> resultTable = HashBasedTable.create();

    for (List<Translator> group : translators) {
      for (Translator translator : group) {
        var translatorFunction = translator.constructor;

        for (LabelledFormula formula : formulaSet) {
          var summary = AutomatonSummary.of(() -> translatorFunction.apply(formula));
          var negationSummary = AutomatonSummary.of(() -> translatorFunction.apply(formula.not()));

          if (summary != null) {
            resultTable.put(formula.formula(), translator.name, summary);
          }

          if (negationSummary != null) {
            resultTable.put(formula.formula().not(), translator.name, negationSummary);
          }
        }
      }
    }

    var latexReport = LatexReport.create(
      translators.stream()
        .map(x -> Collections3.transformList(x, y -> y.name))
        .toList(),
      Map.of(),
      set.name().toLowerCase(),
      formulaSet.stream().map(LabelledFormula::formula).toList(),
      resultTable,
      EmersonLeiAcceptance::acceptanceSets, 10, false);

    try (Writer writer = Files.newBufferedWriter(
      Paths.get(set.name() + ".tex"), StandardCharsets.UTF_8)) {
      writer.write(latexReport);
    }
  }

  @Disabled
  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"})
  void generateIntermediateStttLatexReport(FormulaSet set) throws IOException {
    var configuration = RabinizerConfiguration.of(true, true, true);

    // Without Portfolio.

    var dpa_ldba_asymmetric = new Translator("\\LDone",
        LtlTranslationRepository.LtlToLdbaTranslation.SEJK16.translation(BuchiAcceptance.class,
            EnumSet.noneOf(
                LtlTranslationRepository.Option.class)));

    var dpa_ldba_symmetric = new Translator("\\LDtwo",
        LtlTranslationRepository.LtlToLdbaTranslation.EKS20.translation(BuchiAcceptance.class,
            EnumSet.noneOf(
                LtlTranslationRepository.Option.class)));

    var dpa_iar_asymmetric = new Translator("\\Done", formula -> {
      var dgra = RabinizerBuilder.build(formula, configuration);
      var optimisedDgra = OmegaAcceptanceCast.cast(
        AcceptanceOptimizations.transform(dgra), GeneralizedRabinAcceptance.class);

      var dra = RabinDegeneralization.degeneralize(optimisedDgra);
      var optimisedDra = OmegaAcceptanceCast.cast(
        AcceptanceOptimizations.transform(dra), RabinAcceptance.class);

      return optimisedDra;
    });

    var dpa_iar_symmetric = new Translator("\\Dtwo", formula -> {
      var dra = SymmetricDRAConstruction.of(RabinAcceptance.class, true).apply(formula);
      var optimisedDra = OmegaAcceptanceCast.cast(
        AcceptanceOptimizations.transform(dra), RabinAcceptance.class);
      return optimisedDra;
    });

    // With Portfolio

    var translators = List.of(
      List.of(dpa_iar_asymmetric, dpa_iar_symmetric, dpa_ldba_asymmetric, dpa_ldba_symmetric));
    var formulaSet = set.loadAndDeduplicateFormulaSet();

    Table<Formula, String, AutomatonSummary> resultTable = HashBasedTable.create();

    for (List<Translator> group : translators) {
      for (Translator translator : group) {
        var translatorFunction = translator.constructor;

        for (LabelledFormula formula : formulaSet) {
          var summary = AutomatonSummary.of(() -> translatorFunction.apply(formula));
          var negationSummary = AutomatonSummary.of(() -> translatorFunction.apply(formula.not()));

          if (summary != null) {
            resultTable.put(formula.formula(), translator.name, summary);
          }

          if (negationSummary != null) {
            resultTable.put(formula.formula().not(), translator.name, negationSummary);
          }
        }
      }
    }

    var latexReport = LatexReport.create(
      translators.stream()
        .map(x -> Collections3.transformList(x, y -> y.name))
        .toList(),
      Map.of(),
      set.name().toLowerCase(),
      formulaSet.stream().map(LabelledFormula::formula).toList(),
      resultTable,
      EmersonLeiAcceptance::acceptanceSets, 100, false);

    try (Writer writer = Files.newBufferedWriter(
      Paths.get(set.name() + ".tex"), StandardCharsets.UTF_8)) {
      writer.write(latexReport);
    }
  }

  static class LatexReport {
    private final NavigableSet<Map.Entry<Double, Formula>> sortedSet = new TreeSet<>(
      Map.Entry.<Double, Formula>comparingByKey()
        .thenComparing(Map.Entry.<Double, Formula>comparingByValue().reversed())
    );

    // Column configuration
    private final List<List<String>> tableHeader;
    private final Map<String, String> readableTranslatorNames;

    // Row configuration
    private final String formulaSetName;
    private final BiMap<Formula, Integer> formulaEnumeration;

    // Results
    private final Table<Formula, String, AutomatonSummary> results;

    private int tableCounter = 0;

    private final boolean rotateHeader;

    private LatexReport(List<List<String>> tableHeader,
      Map<String, String> readableTranslatorNames,
      String formulaSetName,
      List<Formula> formulas,
      Table<Formula, String, AutomatonSummary> results,
      boolean rotateHeader) {
      // Column configuration
      this.tableHeader = tableHeader;
      this.readableTranslatorNames = readableTranslatorNames;

      // Row configuration
      this.formulaSetName = formulaSetName;
      this.formulaEnumeration = HashBiMap.create();

      for (var formula : formulas) {
        if (!formulaEnumeration.containsKey(formula.not())) {
          assert !formulaEnumeration.containsKey(formula);
          formulaEnumeration.put(formula, formulaEnumeration.size());
        }
      }

      // Results
      this.results = results;

      results.rowMap().forEach((formula, formulaResults) -> {
        int lowerBound = -1;
        int upperBound = -1;

        for (var toolResult : formulaResults.entrySet()) {
          var summary = toolResult.getValue();

          if (lowerBound == -1) {
            assert upperBound == -1;
            lowerBound = summary.size;
            upperBound = summary.size;
          } else {
            lowerBound = Math.min(lowerBound, summary.size);
            upperBound = Math.max(upperBound, summary.size);
          }
        }

        double differenceOrderOfMagnitude = Math.log10((upperBound + 1.0d) / (lowerBound + 1.0d));
        sortedSet.add(Map.entry(differenceOrderOfMagnitude, formula));
      });

      this.rotateHeader = rotateHeader;
    }

    static String create(List<List<String>> groupedTranslators,
      Map<String, String> readableTranslatorNames,
      String formulaSetName,
      List<Formula> formulas,
      Table<Formula, String, AutomatonSummary> results,
      Function<EmersonLeiAcceptance, Integer> normalisation,
      int threshold,
      boolean rotateHeader) {
      return new LatexReport(groupedTranslators, readableTranslatorNames,
        formulaSetName, formulas, results, rotateHeader).report(threshold, normalisation);
    }

    private int[] extract_size(String tool) {
      return results.column(tool).values().stream().mapToInt(x -> x.size).toArray();
    }

    private String report(int lines, Function<EmersonLeiAcceptance, Integer> normalisation) {
      Set<Formula> referencedFormulas = new HashSet<>();
      String mainTables = createTables(false, lines, normalisation, referencedFormulas);
      String referencedFormulasDescription = createFormulaTables(referencedFormulas);
      String appendixTables = createTables(true, lines, normalisation, null);
      String allFormulasDescription = createFormulaTables(formulaEnumeration.keySet());

      return String.format("%s\n%s\\newcommand{\\%sAppendix}{%s\n%s}",
        mainTables,
        referencedFormulasDescription,
        formulaSetName.replaceAll("[0-9]", ""),
        appendixTables,
        allFormulasDescription);
    }

    private String statsRow(String identifier, Function<int[], String> computation) {
      StringBuilder row = new StringBuilder("    ")
        .append(identifier)
        .append(" \n      ");

      tableHeader.forEach(groupHeader ->
        groupHeader.forEach(tool -> {
          String renderedValue = computation.apply(extract_size(tool));
          int index = renderedValue.indexOf('.');

          if (index < 0) {
            row.append("& n/a & ");
          } else {
            row.append(String.format("& %s & {\\hspace{-0.25em}%s}",
              renderedValue.substring(0, index),
              renderedValue.substring(index)));
          }
        }));

      row.append("\\\\\n");

      return row.toString();
    }

    private List<Formula> rowsToPrint(int lines, boolean appendix) {
      List<Formula> content = new ArrayList<>();
      var descendingIterator = sortedSet.descendingIterator();

      for (int i = 0; i < lines && descendingIterator.hasNext(); i++) {
        var formula = descendingIterator.next().getValue();

        if (!appendix) {
          content.add(formula);
        }
      }

      if (appendix) {
        descendingIterator.forEachRemaining(x -> content.add(x.getValue()));
      }

      return content;
    }

    private void formulaTableHeader(StringBuilder tableBuilder) {
      tableBuilder.append("\n\\begin{table}[hbt]\n");
      tableBuilder.append("  \\scriptsize\n");
      tableBuilder.append("  \\begin{tabularx}{\\linewidth}{c|p{0.89\\linewidth}}\n");
      tableBuilder.append("  \\toprule\n");
    }

    private void formulaTableFooter(StringBuilder tableBuilder) {
      tableBuilder.append("    \\bottomrule\n");
      tableBuilder.append("  \\end{tabularx}\n");
      tableBuilder.append(String.format("  \\caption{Formulas from %s (part %d).}\n",
        formulaSetName, tableCounter));
      tableBuilder.append(String.format("  \\label{exp:table:%s:form:%d}\n",
        formulaSetName, tableCounter));
      tableBuilder.append("\\end{table}\n");
    }

    private String createFormulaTables(Set<Formula> formulas) {
      var sortedFormulas = new ArrayList<>(formulas);
      sortedFormulas.sort(Comparator.comparingInt(formulaEnumeration::get));

      var tableBuilder = new StringBuilder(200 * sortedFormulas.size());
      formulaTableHeader(tableBuilder);

      int count = 0;

      for (Formula formula : sortedFormulas) {
        tableBuilder.append("    $\\varphi_{")
          .append(formulaEnumeration.get(formula) + 1)
          .append("}$ & {\\scriptsize $")
          .append(formula.accept(PRINT_VISITOR))
          .append("$} \\\\\n");

        count++;

        if (count == 40) {
          count = 0;
          formulaTableFooter(tableBuilder);
          formulaTableHeader(tableBuilder);
        }
      }

      formulaTableFooter(tableBuilder);
      return tableBuilder.toString();
    }

    private String createTables(boolean appendix, int lines,
      Function<EmersonLeiAcceptance, Integer> normalisation, @Nullable Set<Formula> referencedFormulas) {

      var tableBuilder = new StringBuilder(tableHeader());

      var rowsToPrint = rowsToPrint(lines, appendix);
      int rowsPrinted = 0;

      for (Formula formula : rowsToPrint) {
        boolean negatedRow = !formulaEnumeration.containsKey(formula);
        var canonicalFormula = negatedRow ? formula.not() : formula;

        if (referencedFormulas != null) {
          referencedFormulas.add(canonicalFormula);
        }

        if (negatedRow) {
          tableBuilder.append("    $\\overline{\\varphi_{")
            .append(formulaEnumeration.get(canonicalFormula) + 1)
            .append("}}$ \n");
        } else {
          tableBuilder.append("    $\\varphi_{")
            .append(formulaEnumeration.get(canonicalFormula) + 1)
            .append("}$ \n");
        }

        tableBuilder.append("      ");

        for (List<String> groupHeader : tableHeader) {
          int smallestSize = Integer.MAX_VALUE;

          for (String tool : groupHeader) {
            var properties = results.get(formula, tool);

            if (properties != null) {
              smallestSize = Math.min(smallestSize, properties.size);
            }
          }

          for (String tool : groupHeader) {
            AutomatonSummary summary = results.get(formula, tool);

            if (summary == null) {
              tableBuilder.append("& n/a & ");
              continue;
            }

            tableBuilder.append(String.format("& %d ", summary.size));

            int normalisedSets = normalisation.apply(summary.acceptance);

            if (normalisedSets > 1) {
              tableBuilder.append(String.format("& (%d) ", normalisedSets));
            } else {
              tableBuilder.append("&      ");
            }
          }
        }

        tableBuilder.append("\\\\\n");
        rowsPrinted = rowsPrinted + 1;

        if (rowsPrinted == 60) {
          tableBuilder.append(tableFooter(false, sortedSet.size() - lines));
          rowsPrinted = 0;
          tableBuilder.append(tableHeader());
        }
      }

      // Print statistics
      if (!appendix) {
        tableBuilder.append("    \\midrule\n");

        // Mean
        tableBuilder.append(statsRow(
          "$\\frac{1}{n}\\Sigma$",
          sizes -> String.format("%.2f", Statistics.arithmeticMean(sizes))));

        // Standard deviation
        tableBuilder.append(statsRow(
          "$\\sigma$",
          sizes -> String.format("%.2f", Statistics.standardDeviation(sizes))));

        //Geometric Mean
        tableBuilder.append(statsRow(
          "$\\sqrt[\\uproot{2}n]{\\Pi}$",
          sizes -> String.format("%.2f", Statistics.geometricMean(sizes))));

        // Median
        tableBuilder.append(statsRow(
          "med.",
          sizes -> String.format("%.1f", Statistics.median(sizes))));
      }

      tableBuilder.append(tableFooter(appendix, sortedSet.size() - lines));
      return tableBuilder.toString();
    }

    private StringBuilder tableHeader() {
      tableCounter++;

      var header = new StringBuilder();

      header.append("\n\\begin{table}[hbt]\n");
      header.append("  \\centering\n");
      header.append("  \\scriptsize\n");
      header.append("  \\begin{tabularx}{0.40\\linewidth}{c|");
      header.append(this.tableHeader.stream().map(group ->
        IntStream.range(0, group.size())
          .mapToObj(x -> "r@{\\hspace{0.33\\tabcolsep}}l")
          .collect(Collectors.joining("@{\\hspace{1.5\\tabcolsep}}")))
        .collect(Collectors.joining("|")));
      header.append("X}\n");

      if (rotateHeader) {
        header.append("    \\rot{LTL}");
      } else {
        header.append("    LTL");
      }

      for (List<String> group : this.tableHeader) {
        for (String translator : group) {
          if (rotateHeader) {
            header.append("& \\rotTwo{");
          } else {
            header.append("& ");
          }

          header.append(readableTranslatorNames.getOrDefault(translator, translator));

          if (rotateHeader) {
            header.append("} ");
          } else {
            header.append(" & ");
          }
        }
      }

      header.append("\\\\\n");
      header.append("    \\toprule\n");
      return header;
    }

    private StringBuilder tableFooter(boolean appendix, int skippedRows) {
      var comment = String.format(
        "Filtered %s from %s formulas (%.2f) and moved them to the appendix.",
        skippedRows,
        2 * formulaEnumeration.size(), ((double) skippedRows) / (2.0 * formulaEnumeration.size()));

      StringBuilder footer = new StringBuilder(100);
      footer.append("    \\bottomrule\n");
      footer.append("  \\end{tabularx}\n");
      footer.append(String.format("  \\caption{Formulas from %s (part %d). "
          + "Listing number of states and number of Rabin pairs (if larger than 1). (%s)}\n",
        formulaSetName, tableCounter, appendix ? "" : comment));
      footer.append(String.format("  \\label{exp:table:%s:%d}\n",
        formulaSetName, tableCounter));
      footer.append("\\end{table}\n");
      return footer;
    }
  }
}
