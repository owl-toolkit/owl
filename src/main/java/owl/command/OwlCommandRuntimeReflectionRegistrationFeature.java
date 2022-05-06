/*
 * Copyright (C) 2016 - 2021  (See AUTHORS)
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

package owl.command;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import owl.thirdparty.picocli.CommandLine.Command;
import owl.util.OwlVersion;

public class OwlCommandRuntimeReflectionRegistrationFeature implements Feature {

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    try {
      // Register all commands
      registerCommand(OwlCommand.class);

      for (Class<?> subcommand : OwlCommand.class.getAnnotation(Command.class).subcommands()) {
        registerCommand(subcommand);
      }

      var autoHelpMixin = Class.forName("owl.thirdparty.picocli.CommandLine$AutoHelpMixin");
      RuntimeReflection.register(autoHelpMixin);
      RuntimeReflection.register(autoHelpMixin.getDeclaredFields());

      RuntimeReflection.registerForReflectiveInstantiation(OwlVersion.class);

      for (Class<?> mixin : Mixins.class.getNestMembers()) {
        RuntimeReflection.register(mixin);
        RuntimeReflection.register(mixin.getDeclaredConstructors());
        RuntimeReflection.register(mixin.getDeclaredFields());
      }

    } catch (NoSuchMethodException | ClassNotFoundException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void registerCommand(Class<?> command) throws NoSuchMethodException {
    if (!Modifier.isAbstract(command.getModifiers())) {
      RuntimeReflection.registerForReflectiveInstantiation(command);
    }

    if (command.equals(AbstractOwlSubcommand.class)) {
      Field[] declaredFields = command.getDeclaredFields();
      boolean foundField = false;

      // Exclude fields of the class AbstractOwlSubcommand in order to remove the
      // '--run-in-non-native-mode' flag from the help messages and tool.
      for (Field declaredField : declaredFields) {
        if ("nonNativeMode".equals(declaredField.getName())) {
          foundField = true;
        } else {
          RuntimeReflection.register(declaredField);
        }
      }

      if (!foundField) {
        throw new IllegalStateException("Missing nonNativeMode field.");
      }
    } else {
      // We register all fields.
      RuntimeReflection.register(command.getDeclaredFields());
    }

    // Look for nested classes.
    for (var declaredClass : command.getDeclaredClasses()) {
      RuntimeReflection.registerForReflectiveInstantiation(declaredClass);
      RuntimeReflection.register(declaredClass.getDeclaredFields());
    }

    if (!command.equals(AbstractOwlCommand.class)) {
      registerCommand(command.getSuperclass());
    }
  }
}
