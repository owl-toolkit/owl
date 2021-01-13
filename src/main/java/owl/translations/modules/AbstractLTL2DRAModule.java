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

package owl.translations.modules;

import javax.annotation.Nullable;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.Bibliography;
import owl.translations.rabinizer.RabinizerConfiguration;

public final class AbstractLTL2DRAModule {
  private AbstractLTL2DRAModule() {}

  private static Option asymmetric() {
    return new Option("a", "asymmetric", false, "Guess only greatest "
      + "fixed-points (G,R,W) that are almost always true. This corresponds to the construction "
      + "described in [" + Bibliography.FMSD_16_CITEKEY + "].");
  }

  private static Option symmetric() {
    return new Option("s", "symmetric", false, "Guess greatest (G,R,W) "
      + "and least (F,M,U) fixed-points that are almost always respectively infinitely often true. "
      + "This corresponds to the construction described in [" + Bibliography.DISSERTATION_19_CITEKEY
      + ", " + Bibliography.LICS_18_CITEKEY + "]. This is the default selection.");
  }

  private static Option normalForm() {
    return new Option("n", "normal-form", false, "Use the normalisation "
      + "procedure of [" + Bibliography.LICS_20_CITEKEY + "] to rewrite the formula to a formula "
      + "from the Δ₂-fragment of LTL. For this fragment simple translation for to deterministic "
      + "automata are known [" + Bibliography.LICS_20_CITEKEY + ", "
      + Bibliography.DISSERTATION_19_CITEKEY + "].");
  }

  private static OptionGroup getOptionGroup() {
    var group = new OptionGroup();
    group.addOption(asymmetric());
    group.addOption(symmetric());
    group.addOption(normalForm());
    return group;
  }

  private static Options getAsymmetricOptions() {
    return new Options()
      .addOption("ne", "noeager", false,
        "Disable eager construction. Only affects asymmetric construction.")
      .addOption("np", "nosuspend", false,
        "Disable suspension detection. Only affects asymmetric construction.")
      .addOption("ns", "nosupport", false,
        "Disable support based relevant formula analysis. Only affects asymmetric construction.");
  }

  static Options options() {
    return getAsymmetricOptions()
      .addOptionGroup(getOptionGroup())
      .addOption(AbstractLTL2PortfolioModule.disablePortfolio());
  }

  static Translation parseTranslator(CommandLine commandLine) {
    if (commandLine.hasOption(asymmetric().getOpt())) {
      return Translation.ASYMMETRIC;
    }

    if (commandLine.hasOption(normalForm().getOpt())) {
      return Translation.NORMAL_FORM;
    }

    // Symmetric is the default construction.
    return Translation.SYMMETRIC;
  }

  @Nullable
  static RabinizerConfiguration parseAsymmetric(CommandLine commandLine) {
    if (commandLine.hasOption(symmetric().getOpt())
      || !commandLine.hasOption(asymmetric().getOpt())) {
      return null;
    }

    boolean eager = !commandLine.hasOption("noeager");
    boolean support = !commandLine.hasOption("nosupport");
    boolean suspend = !commandLine.hasOption("nosuspend");

    return RabinizerConfiguration.of(eager, support, suspend);
  }

  public enum Translation {
    SYMMETRIC,
    ASYMMETRIC,
    NORMAL_FORM
  }
}
