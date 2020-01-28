/*
 * Copyright (C) 2016 - 2019  (See AUTHORS)
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import owl.automaton.Automaton;
import owl.automaton.acceptance.BooleanExpressions;
import owl.automaton.acceptance.GeneralizedRabinAcceptance;
import owl.automaton.acceptance.OmegaAcceptance;
import owl.automaton.acceptance.optimizations.AcceptanceOptimizations;
import owl.collections.Collections3;
import owl.ltl.Formula;
import owl.ltl.LabelledFormula;
import owl.ltl.visitors.LatexPrintVisitor;
import owl.run.Environment;
import owl.translations.ExternalTranslator.InputMode;
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
  void generateComparison(FormulaSet set) throws IOException {
    Function<LabelledFormula, Automaton<?, ?>> dgraAsymmetric = (LabelledFormula formula) ->
      AcceptanceOptimizations.optimize(
        RabinizerBuilder.build(formula, Environment.standard(), RabinizerConfiguration.of(true, true, true)));

    Function<LabelledFormula, Automaton<?, ?>> dgraSymmetric = (LabelledFormula formula) ->
      AcceptanceOptimizations.optimize(
        SymmetricDRAConstruction.of(Environment.standard(), GeneralizedRabinAcceptance.class, true)
          .apply(formula));

    int symmeticLeq = 0;
    int completeCount = 0;

    Set<LabelledFormula> seenFormulas = new HashSet<>();

    for (LabelledFormula formula : set.loadAndDeduplicateFormulaSet()) {
      if (seenFormulas.contains(formula) || seenFormulas.contains(formula.not())) {
        continue;
      }

      completeCount = completeCount + 2;

      if (dgraSymmetric.apply(formula).size() <= dgraAsymmetric.apply(formula).size()) {
        symmeticLeq++;
      }

      if (dgraSymmetric.apply(formula.not()).size() <= dgraAsymmetric.apply(formula.not()).size()) {
        symmeticLeq++;
      }

      seenFormulas.add(formula);
      seenFormulas.add(formula.not());
    }


    System.out.println(symmeticLeq + "/" + completeCount);
  }

  @Tag("size-report")
  @ParameterizedTest
  @EnumSource(
    value = FormulaSet.class,
    names = {"DWYER", "PARAMETRISED"}) // "LIBEROUTER", "PELANEK", "ETESSAMI", "SOMENZI",
  void generateLatexReport(FormulaSet set) throws IOException {
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

    var dgraAsymmetric = new Translator("DGRA (asymmetric)", (env) -> (formula) ->
      AcceptanceOptimizations.optimize(
        RabinizerBuilder.build(formula, env,
          RabinizerConfiguration.of(true, true, true))));

    var dgraSymmetric = new Translator("DGRA (this paper)", environment -> formula ->
      AcceptanceOptimizations.optimize(
        SymmetricDRAConstruction.of(environment, GeneralizedRabinAcceptance.class, true)
          .apply(formula)));

    var historic = List.of(new Translator("ltl2dstar (historic)", environment
      -> new ExternalTranslator(ltl2dstarCommand, InputMode.REPLACE, environment)));

    var portfolio = List.of(new Translator("ltl2tgba (portfolio)", environment
      -> new ExternalTranslator(ltl2tgbaCommand, InputMode.REPLACE, environment)));

    var direct = List.of(dgraAsymmetric, dgraSymmetric);
    var translators = List.of(historic, direct, portfolio);
    var formulaSet = set.loadAndDeduplicateFormulaSet();

    Table<Formula, String, AutomatonSummary> resultTable = HashBasedTable.create();

    for (List<Translator> group : translators) {
      for (Translator translator : group) {
        var translatorFunction = translator.constructor.apply(Environment.standard());

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
        .collect(Collectors.toList()),
      Map.of(),
      set.name().toLowerCase(),
      formulaSet.stream().map(LabelledFormula::formula).collect(Collectors.toList()),
      resultTable,
      z -> BooleanExpressions.toDnf(z.booleanExpression()).size(), 25);

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

    private LatexReport(List<List<String>> tableHeader,
      Map<String, String> readableTranslatorNames,
      String formulaSetName,
      List<Formula> formulas,
      Table<Formula, String, AutomatonSummary> results) {
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
    }

    static String create(List<List<String>> groupedTranslators,
      Map<String, String> readableTranslatorNames,
      String formulaSetName,
      List<Formula> formulas,
      Table<Formula, String, AutomatonSummary> results,
      Function<OmegaAcceptance, Integer> normalisation, int threshold) {
      return new LatexReport(groupedTranslators, readableTranslatorNames,
        formulaSetName, formulas, results).report(threshold, normalisation);
    }

    private int[] extract_size(String tool) {
      return results.column(tool).values().stream().mapToInt(x -> x.size).toArray();
    }

    private String report(int lines, Function<OmegaAcceptance, Integer> normalisation) {
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
          int index = renderedValue.indexOf(".");
          row.append(String.format("& %s & {\\hspace{-0.25em}%s}",
            renderedValue.substring(0, index),
            renderedValue.substring(index)));
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
      Function<OmegaAcceptance, Integer> normalisation, @Nullable Set<Formula> referencedFormulas) {

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

        if (rowsPrinted == 30) {
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
      header.append("    \\rot{LTL}");

      for (List<String> group : this.tableHeader) {
        for (String translator : group) {
          header.append("& \\rotTwo{");
          header.append(readableTranslatorNames.getOrDefault(translator, translator));
          header.append("} ");
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
