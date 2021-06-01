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

package owl.cinterface;

import java.util.List;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;
import owl.util.OwlVersion;

@CContext(CInterface.CDirectives.class)
public final class CInterface {

  public static final String CALL_DESTROY
    = "This function returns a void pointer to an opaque Java object handle. The object is not "
    + "collected by the garbage collected unless 'destroy_object_handle' is called on the pointer.";

  public static final String CHAR_TO_STRING
    = "Decodes a 0 terminated C char* to a Java string using the platform's default charset.";

  public static final int SEPARATOR = -232_323;

  public static final int FEATURE_SEPARATOR = -424_242;

  private CInterface() {}

  @CEntryPoint(
    name = "destroy_object_handle",
    exceptionHandler = PrintStackTraceAndExit.ReturnVoid.class
  )
  public static void destroyObjectHandle(IsolateThread thread, ObjectHandle handle) {
    ObjectHandles.getGlobal().destroy(handle);
  }

  @CEntryPoint(
    name = "free_unmanaged_memory",
    exceptionHandler = PrintStackTraceAndExit.ReturnVoid.class
  )
  public static void freeUnmanagedMemory(IsolateThread thread, PointerBase ptr) {
    UnmanagedMemory.free(ptr);
  }

  @CEntryPoint(
    name = "print_object_handle",
    exceptionHandler = PrintStackTraceAndExit.ReturnUnsignedWord.class
  )
  public static UnsignedWord printObjectHandle(
    IsolateThread thread, ObjectHandle handle, CCharPointer buffer, UnsignedWord bufferSize) {

    return CTypeConversion.toCString(
      ObjectHandles.getGlobal().get(handle).toString(), buffer, bufferSize);
  }

  @CEntryPoint(
    name = "owl_version",
    exceptionHandler = PrintStackTraceAndExit.ReturnUnsignedWord.class
  )
  public static UnsignedWord owlVersion(
    IsolateThread thread, CCharPointer buffer, UnsignedWord bufferSize) {

    return CTypeConversion.toCString(
      OwlVersion.getNameAndVersion().version(), buffer, bufferSize);
  }

  public static class CDirectives implements CContext.Directives {

    @Override
    public List<String> getHeaderFiles() {
      // The header file with the C declarations that are imported.
      var headerLocation = System.getProperty("owlHeader");

      if (headerLocation == null) {
        throw new IllegalArgumentException("Location of header file is missing."
          + "Use -DowlHeader=/foo/bar/ to define location.");
      }

      return List.of(String.format("\"%s/owltypes.h\"", headerLocation));
    }
  }

  static final class PrintStackTraceAndExit {

    private PrintStackTraceAndExit() {}

    static void handleException(Throwable throwable) {
      throwable.printStackTrace(System.err);
      System.err.flush();
      System.exit(-1);
    }

    static final class ReturnObjectHandle {
      private ReturnObjectHandle() {}

      static ObjectHandle handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return ObjectHandles.getGlobal().create(null);
      }
    }

    static final class ReturnUnsignedWord {
      private ReturnUnsignedWord() {}

      static UnsignedWord handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return WordFactory.unsigned(Long.MIN_VALUE);
      }
    }

    static final class ReturnBoolean {
      private ReturnBoolean() {}

      static boolean handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return false;
      }
    }

    static final class ReturnInt {
      private ReturnInt() {}

      static int handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return Integer.MIN_VALUE;
      }
    }

    static final class ReturnAcceptance {
      private ReturnAcceptance() {}

      static CAutomaton.Acceptance handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return CAutomaton.Acceptance.BOTTOM;
      }
    }

    static final class ReturnRealizabilityStatus {
      private ReturnRealizabilityStatus() {}

      static CDecomposedDPA.RealizabilityStatus handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return CDecomposedDPA.RealizabilityStatus.UNKNOWN;
      }
    }

    static final class ReturnNodeType {
      private ReturnNodeType() {}

      static CDecomposedDPA.Structure.NodeType handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
        return CDecomposedDPA.Structure.NodeType.AUTOMATON;
      }
    }

    static final class ReturnVoid {
      private ReturnVoid() {}

      static void handleException(Throwable throwable) {
        PrintStackTraceAndExit.handleException(throwable);
      }
    }
  }

  @CConstant("OWL_INITIAL_STATE")
  public static native int owlInitialState();

  @CConstant("OWL_ACCEPTING_SINK")
  public static native int owlAcceptingSink();

  @CConstant("OWL_REJECTING_SINK")
  public static native int owlRejectingSink();

  @CConstant("OWL_SEPARATOR")
  public static native int owlSeparator();

  @CConstant("OWL_FEATURE_SEPARATOR")
  public static native int owlFeatureSeparator();

  public static void main(String... args) {
    // Empty method for native-image.
  }
}
