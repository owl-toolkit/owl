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

package owl.run.modules;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

@SuppressWarnings("InterfaceMayBeAnnotatedFunctional")
public interface OwlModuleParser<M extends OwlModule> {
  default String getDescription() {
    return "";
  }

  String getKey();

  default Options getOptions() {
    return new Options();
  }

  M parse(CommandLine commandLine) throws ParseException;

  interface ReaderParser extends OwlModuleParser<InputReader> {}

  interface TransformerParser extends OwlModuleParser<Transformer> {}

  interface WriterParser extends OwlModuleParser<OutputWriter> {}
}
