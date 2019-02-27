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

package owl.translations.ltl2ldba;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import owl.run.modules.OwlModuleParser;

public abstract class AbstractLTL2LDBAModule implements OwlModuleParser.TransformerParser {

  public static Option asymmetric() {
    return new Option("a", "asymmetric", false, "Guess only greatest "
      + "fixed-points (G,R,W) that are almost always true. This corresponds to the construction "
      + "described in [SEJK: CAV'16].");
  }

  public static Option symmetric() {
    return new Option("s", "symmetric", false, "Guess greatest (G,R,W) "
      + "and least (F,M,U) fixed-points that are almost always respectively infinitely often true. "
      + "This corresponds to the construction described in [EKS: LICS'18].");
  }

  public static OptionGroup getOptionGroup() {
    var group = new OptionGroup();
    group.addOption(asymmetric());
    group.addOption(symmetric());
    return group;
  }

  @Override
  public Options getOptions() {
    return new Options().addOptionGroup(getOptionGroup());
  }
}
