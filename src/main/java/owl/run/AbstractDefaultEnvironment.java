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

import java.util.concurrent.atomic.AtomicBoolean;
import org.immutables.value.Value;
import owl.factories.FactorySupplier;
import owl.factories.jbdd.JBddSupplier;

@Value.Immutable
@Value.Style(builderVisibility = Value.Style.BuilderVisibility.PUBLIC,
             visibility = Value.Style.ImplementationVisibility.PUBLIC,
             typeImmutable = "*")
abstract class AbstractDefaultEnvironment implements Environment {
  private final AtomicBoolean shutdown = new AtomicBoolean(false);

  @Value.Parameter
  @Override
  public abstract boolean annotations();

  @Value.Derived
  @Override
  public FactorySupplier factorySupplier() {
    return JBddSupplier.async(annotations());
  }

  @Override
  public void shutdown() {
    shutdown.lazySet(true);
  }

  @Override
  public boolean isShutdown() {
    return shutdown.get();
  }

  public static Environment annotated() {
    return DefaultEnvironment.of(true);
  }

  public static Environment standard() {
    return DefaultEnvironment.of(false);
  }
}
