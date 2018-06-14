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

package owl.translations.rabinizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import owl.ltl.LabelledFormula;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformer;
import owl.run.modules.Transformers;

public final class RabinizerCliParser implements TransformerParser {
  public static final RabinizerCliParser INSTANCE = new RabinizerCliParser();

  private RabinizerCliParser() {}

  @Override
  public String getKey() {
    return "ltl2dgra";
  }

  @Override
  public String getDescription() {
    return "Translates LTL to deterministic generalized Rabin automata, using the Rabinizer "
      + "construction";
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public Options getOptions() {
    return new Options()
      .addOption("ne", "noeager", false, "Disable eager construction")
      .addOption("np", "nosuspend", false, "Disable suspension detection")
      .addOption("ns", "nosupport", false, "Disable support based relevant formula analysis")
      .addOption("na", "noacceptance", false, "Disable generation of acceptance condition")
      .addOption("c", "complete", false, "Build complete automaton");
  }

  @SuppressWarnings("SpellCheckingInspection")
  @Override
  public Transformer parse(CommandLine commandLine) {
    boolean eager = !commandLine.hasOption("noeager");
    boolean support = !commandLine.hasOption("nosupport");
    boolean acceptance = !commandLine.hasOption("noacceptance");
    boolean complete = commandLine.hasOption("complete");
    boolean suspend = !commandLine.hasOption("nosuspend");
    ImmutableRabinizerConfiguration configuration = ImmutableRabinizerConfiguration.builder()
      .eager(eager)
      .supportBasedRelevantFormulaAnalysis(support)
      .computeAcceptance(acceptance)
      .completeAutomaton(complete)
      .suspendableFormulaDetection(suspend)
      .build();

    return environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      formula -> RabinizerBuilder.build(formula, environment, configuration));
  }
}
