/*
 * Copyright (C) 2016 - 2020  (See AUTHORS)
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

package owl.util;

import com.google.auto.value.AutoValue;

public class OwlVersion {

  // Fall-back strings if MANIFEST cannot be accessed correctly.
  private static final String MAIN_NAME = "owl";
  private static final String VERSION = "21-ml";

  private OwlVersion() {}

  /**
   * Obtains the name and version of the currently running Owl component. This is done by searching
   * the current stack trace for the initial entry point. It is assumed that this is called from the
   * main thread.
   */
  public static NameAndVersion getNameAndVersion() {
    String moduleName;

    try {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      StackTraceElement main = stack[stack.length - 1];
      moduleName = Class.forName(main.getClassName())
        .getSimpleName().toLowerCase().replace("module", "");
    } catch (ArrayIndexOutOfBoundsException | ClassNotFoundException e) {
      moduleName = null;
    }

    Package owlPackage = OwlVersion.class.getPackage();

    String mainName = owlPackage.getImplementationTitle();

    if (mainName == null) {
      mainName = MAIN_NAME;
    } else if (!mainName.equals(MAIN_NAME)) {
      throw new IllegalStateException("Conflicting main names.");
    }

    String version = owlPackage.getImplementationVersion();

    if (version == null) {
      version = VERSION;
    } else if (!version.equals(VERSION)) {
      throw new IllegalStateException("Conflicting versions.");
    }

    if (moduleName == null) {
      return NameAndVersion.of(mainName, version);
    }

    return NameAndVersion.of(String.format("%s (%s)", moduleName, mainName), version);
  }

  @AutoValue
  public abstract static class NameAndVersion {
    public abstract String name();

    public abstract String version();

    private static NameAndVersion of(String name, String version) {
      return new AutoValue_OwlVersion_NameAndVersion(name, version);
    }
  }
}
