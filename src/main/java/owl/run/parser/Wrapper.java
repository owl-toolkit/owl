package owl.run.parser;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.Function;
import owl.run.modules.OwlModule;
import owl.run.modules.OwlModuleParser;

abstract class Wrapper {
  static <T extends OwlModule> Wrapper module(T module) {
    checkNotNull(module);
    return new Module<>(module);
  }

  static <T extends OwlModuleParser<?>> Wrapper settings(T settings) {
    checkNotNull(settings);
    return new Settings<>(settings);
  }

  @SuppressWarnings("NullableProblems") // IntelliJ gets this wrong somehow
  abstract <V> V map(Function<OwlModule, V> moduleFun, Function<OwlModuleParser<?>, V> settingsFun);

  private static final class Module<T extends OwlModule> extends Wrapper {
    final T module;

    Module(T module) {
      this.module = module;
    }

    @Override
    public <V> V map(Function<OwlModule, V> moduleFun,
      Function<OwlModuleParser<?>, V> settingsFun) {
      return moduleFun.apply(module);
    }
  }

  private static final class Settings<T extends OwlModuleParser<?>> extends Wrapper {
    final T settings;

    Settings(T settings) {
      this.settings = settings;
    }

    @Override
    public <V> V map(Function<OwlModule, V> moduleFun,
      Function<OwlModuleParser<?>, V> settingsFun) {
      return settingsFun.apply(settings);
    }
  }
}
