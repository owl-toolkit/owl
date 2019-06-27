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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Function;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleParser;

abstract class Wrapper {
  static <T extends OwlModule> Wrapper module(T module) {
    checkNotNull(module);
    return new ModuleWrapper<>(module);
  }

  static <T extends OwlModuleParser<?>> Wrapper settings(T settings) {
    checkNotNull(settings);
    return new SettingsWrapper<>(settings);
  }

  @SuppressWarnings("NullableProblems") // IntelliJ gets this wrong somehow
  abstract <V> V map(Function<OwlModule, V> moduleFun, Function<OwlModuleParser<?>, V> settingsFun);

  private static final class ModuleWrapper<T extends OwlModule> extends Wrapper {
    final T module;

    ModuleWrapper(T module) {
      this.module = module;
    }

    @Override
    public <V> V map(Function<OwlModule, V> moduleFun,
      Function<OwlModuleParser<?>, V> settingsFun) {
      return moduleFun.apply(module);
    }
  }

  private static final class SettingsWrapper<T extends OwlModuleParser<?>> extends Wrapper {
    final T settings;

    SettingsWrapper(T settings) {
      this.settings = settings;
    }

    @Override
    public <V> V map(Function<OwlModule, V> moduleFun,
      Function<OwlModuleParser<?>, V> settingsFun) {
      return settingsFun.apply(settings);
    }
  }
}
