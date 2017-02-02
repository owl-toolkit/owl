/*
 * Copyright (C) 2016  (See AUTHORS)
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

import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class NativeLibraryLoader {

  private NativeLibraryLoader() {
  }

  private static String getLibrarySuffix(OperatingSystem operatingSystem) {
    switch (operatingSystem) {
      case DARWIN:
        return ".dylib";

      case LINUX:
      case UNKNOWN:
      default:
        return ".so";
    }
  }

  public static void loadLibrary(String libraryName) throws IOException {
    try {
      System.loadLibrary(libraryName);
    } catch (UnsatisfiedLinkError error) {
      loadLibraryFromJar(libraryName);
    }
  }

  /**
   * Loads library from current JAR archive
   * The file from JAR is copied into system temporary directory and then loaded. The temporary file
   * is deleted after exiting. Method uses String as filename because the pathname is "abstract",
   * not system-dependent.
   *
   * @param libraryName
   *     The name of the library. Platform specific suffixes are handled by the method.
   *
   * @throws IOException
   *     If temporary file creation or read/write operation fails
   */
  private static void loadLibraryFromJar(String libraryName) throws IOException {
    String librarySuffix = getLibrarySuffix(OperatingSystem.getCurrentOperatingSystem());

    // Prepare temporary file
    Path temp = Files.createTempFile("lib" + libraryName, librarySuffix).toAbsolutePath();
    temp.toFile().deleteOnExit();

    if (!Files.exists(temp)) {
      throw new FileNotFoundException("File " + temp + " does not exist.");
    }

    try (InputStream is =
           NativeLibraryLoader.class.getResourceAsStream("lib" + libraryName + librarySuffix)) {
      // Open and check input stream
      if (is == null) {
        throw new FileNotFoundException(
          "File lib" + libraryName + librarySuffix + " was not found inside JAR.");
      }

      try (OutputStream os = new BufferedOutputStream(Files.newOutputStream(temp))) {
        ByteStreams.copy(is, os);
      }
    }

    System.load(temp.toString());
  }

  enum OperatingSystem {
    DARWIN, LINUX, UNKNOWN;

    static OperatingSystem getCurrentOperatingSystem() {
      String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);

      if (osName.contains("mac") || osName.contains("darwin")) {
        return DARWIN;
      }

      if (osName.contains("linux")) {
        return LINUX;
      }

      return UNKNOWN;
    }
  }
}
