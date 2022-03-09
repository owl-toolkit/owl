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

package owl.util;

import owl.thirdparty.picocli.CommandLine;

public final class OwlVersion implements CommandLine.IVersionProvider {

  // Fall-back strings if MANIFEST cannot be accessed correctly.
  private static final String NAME = "owl";
  private static final String VERSION = "22.0-development";

  private OwlVersion() {}

  @Override
  public String[] getVersion() throws Exception {
    var nameAndVersion = getNameAndVersion();
    return new String[]{nameAndVersion.name() + " (version: " + nameAndVersion.version() + ')'};
  }

  /**
   * Obtains the name and version of the currently running Owl component. This is done by searching
   * the current stack trace for the initial entry point. It is assumed that this is called from the
   * main thread.
   */
  public static NameAndVersion getNameAndVersion() {
    Package owlPackage = OwlVersion.class.getPackage();

    String mainName = owlPackage.getImplementationTitle();

    if (mainName == null) {
      mainName = NAME;
    } else if (!mainName.equals(NAME)) {
      throw new IllegalStateException("Conflicting main names.");
    }

    String version = owlPackage.getImplementationVersion();

    if (version == null) {
      version = VERSION;
    } else if (!version.equals(VERSION)) {
      throw new IllegalStateException("Conflicting versions.");
    }

    return new NameAndVersion(mainName, version);
  }

  public record NameAndVersion(String name, String version) {}
}
