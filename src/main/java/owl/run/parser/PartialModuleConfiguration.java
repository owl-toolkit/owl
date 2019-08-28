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

package owl.run.parser;

import com.google.auto.value.AutoValue;
import java.util.List;
import owl.run.modules.OwlModule;

@AutoValue
public abstract class PartialModuleConfiguration {
  abstract OwlModule<OwlModule.InputReader> input();

  abstract List<OwlModule<OwlModule.Transformer>> preprocessing();

  abstract OwlModule<OwlModule.Transformer> configurableTransformer();

  abstract List<OwlModule<OwlModule.Transformer>> postprocessing();

  abstract OwlModule<OwlModule.OutputWriter> output();

  public static PartialModuleConfiguration of(
    OwlModule<OwlModule.InputReader> input,
    List<OwlModule<OwlModule.Transformer>> preprocessing,
    OwlModule<OwlModule.Transformer> configurableTransformer,
    List<OwlModule<OwlModule.Transformer>> postprocessing,
    OwlModule<OwlModule.OutputWriter> output) {
    return new AutoValue_PartialModuleConfiguration(input, List.copyOf(preprocessing),
      configurableTransformer, List.copyOf(postprocessing), output);
  }
}

