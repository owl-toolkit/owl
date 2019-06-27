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

package owl.run;

import owl.factories.FactorySupplier;

/**
 * The environment makes global configuration available to all parts of the pipeline. For example,
 * it provides an {@link FactorySupplier factory supplier} that is supposed to be used by all
 * implementations.
 */
public interface Environment {
  /**
   * Whether additional information (like semantic state labels) should be included.
   */
  boolean annotations();

  /**
   * Returns the configured {@link FactorySupplier}.
   */
  FactorySupplier factorySupplier();

  /**
   * Whether computations should be parallel.
   */
  boolean parallel();

  // TODO Add shutdown hooks

  /**
   * Called exactly one by the runner, indicating that the computation has ended due to, e.g.,
   * input exhaustion or an error.
   */
  void shutdown();

  /**
   * Whether the computation has finished.
   */
  boolean isShutdown();
}
