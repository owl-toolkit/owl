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

package owl.factories;

import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class NativeLibraryLoader {

  private NativeLibraryLoader() {
  }

  public enum OperatingSystem {
    DARWIN, LINUX, UNKNOWN;

    public static OperatingSystem getCurrentOS() {
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

  public static void loadLibrary(String libraryName) {
    try {
      loadLibraryFromJar(libraryName);
    } catch (IOException error) {
      System.loadLibrary(libraryName);
    }
  }

  /**
   * Loads library from current JAR archive
   *
   * The file from JAR is copied into system temporary directory and then loaded. The temporary file is deleted after exiting.
   * Method uses String as filename because the pathname is "abstract", not system-dependent.
   *
   * @param libraryName The name of the library. Platform specific suffixes are handled by the method.
   * @throws IOException If temporary file creation or read/write operation fails
   */
  private static void loadLibraryFromJar(String libraryName) throws IOException {
    String librarySuffix = getLibrarySuffix(OperatingSystem.getCurrentOS());

    // Prepare temporary file
    File temp = File.createTempFile("lib" + libraryName, librarySuffix);
    temp.deleteOnExit();

    if (!temp.exists()) {
      throw new FileNotFoundException("File " + temp.getAbsolutePath() + " does not exist.");
    }

    try (InputStream is = NativeLibraryLoader.class.
      getResourceAsStream("lib" + libraryName + librarySuffix)) {
      // Open and check input stream
      if (is == null) {
        throw new FileNotFoundException("File lib" + libraryName + librarySuffix + " was not found inside JAR.");
      }

      try (OutputStream os = new FileOutputStream(temp)) {
        ByteStreams.copy(is, os);
      }
    }

    System.load(temp.getAbsolutePath());
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
}
