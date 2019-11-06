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

import com.google.auto.value.AutoValue;
import owl.factories.FactorySupplier;
import owl.factories.jbdd.JBddSupplier;

/**
 * The environment makes global configuration available to all parts of the pipeline. For example,
 * it provides an {@link FactorySupplier factory supplier} that is supposed to be used by all
 * implementations.
 */
@AutoValue
public abstract class Environment {
  public abstract boolean annotations();

  public FactorySupplier factorySupplier() {
    return JBddSupplier.async();
  }

  public static Environment of(boolean annotated) {
    return new AutoValue_Environment(annotated);
  }

  public static Environment annotated() {
    return of(true);
  }

  public static Environment standard() {
    return of(false);
  }
}
