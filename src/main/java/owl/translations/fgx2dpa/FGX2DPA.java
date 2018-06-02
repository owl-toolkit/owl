/* Copyright (C) 2016  (See AUTHORS)
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

package owl.translations.fgx2dpa;

import owl.ltl.LabelledFormula;
import owl.run.modules.ImmutableTransformerParser;
import owl.run.modules.InputReaders;
import owl.run.modules.OutputWriters;
import owl.run.modules.OwlModuleParser.TransformerParser;
import owl.run.modules.Transformers;
import owl.run.parser.PartialConfigurationParser;
import owl.run.parser.PartialModuleConfiguration;

public final class FGX2DPA {
  public static final TransformerParser CLI = ImmutableTransformerParser.builder()
    .key("fgx2dpa")
    .description("Translates LTL to a deterministic parity automaton")
    .parser(settings -> environment -> Transformers.instanceFromFunction(LabelledFormula.class,
      formula -> SafetyAutomaton.build(environment, formula)))
    .build();

  private FGX2DPA() {}

  public static void main(String... args) {
    PartialConfigurationParser.run(args, PartialModuleConfiguration.builder("fgx2dpa")
      .reader(InputReaders.LTL)
      .addTransformer(CLI)
      .writer(OutputWriters.HOA)
      .build());
  }
}