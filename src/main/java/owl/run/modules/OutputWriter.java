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

package owl.run.modules;

import java.io.IOException;
import java.io.Writer;
import owl.run.Environment;

/**
 * The final piece of every pipeline, formatting the produced results and writing them on some
 * output. These consumers should be very efficient, since they are effectively blocking the output
 * stream during each call to {@link Binding#write(Object)}, which may degrade performance in
 * parallel invocations.
 *
 * <p>If some implementation runs for a significant amount of time, consider converting it to an
 * {@literal object -> string} transformer and using a {@link OutputWriters#TO_STRING string
 * output}.</p>
 */
@FunctionalInterface
public interface OutputWriter extends OwlModule {
  Binding bind(Writer writer, Environment env);

  @FunctionalInterface
  interface Binding {
    void write(Object object) throws IOException;
  }
}
