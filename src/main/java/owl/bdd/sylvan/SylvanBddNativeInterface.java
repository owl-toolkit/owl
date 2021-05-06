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

package owl.bdd.sylvan;

import java.util.BitSet;
import java.util.List;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.struct.CField;
import org.graalvm.nativeimage.c.struct.CStruct;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

@CContext(SylvanBddNativeInterface.CDirectives.class)
public final class SylvanBddNativeInterface {

  private SylvanBddNativeInterface() {}

  @CFunction("owl_sylvan_init")
  static native void init();

  @CFunction("owl_sylvan_exit")
  static native void exit();

  @CFunction("owl_sylvan_true")
  static native long trueNode();

  @CFunction("owl_sylvan_false")
  static native long falseNode();

  @CFunction("owl_sylvan_var")
  static native long var(int var);

  @CFunction("owl_sylvan_nvar")
  static native long nvar(int var);

  @CFunction("owl_sylvan_not")
  static native long not(long bdd);

  @CFunction("owl_sylvan_gethigh")
  static native long high(long bdd);

  @CFunction("owl_sylvan_getlow")
  static native long low(long bdd);

  @CFunction("owl_sylvan_getvar")
  static native int getvar(long bdd);

  @CFunction("owl_sylvan_and")
  static native long and(long bdd1, long bdd2);

  @CFunction("owl_sylvan_or")
  static native long or(long bdd1, long bdd2);

  @CFunction("owl_sylvan_ite")
  static native long ite(long i, long t, long e);

  @CFunction("owl_sylvan_satcount")
  static native double satcount(long bdd, int nrOfVars);

  @CFunction("owl_sylvan_nodecount")
  static native long nodecount(long bdd);

  @CFunction("owl_sylvan_varset_from_array")
  private static native long varsetFromArray(CIntPointer arr, int len);

  static long varsetFromBitset(BitSet set) {
    int size = set.cardinality();
    CIntPointer arr = UnmanagedMemory.malloc(size * 4);
    for (int i = set.nextSetBit(0), j = 0; i >= 0; i = set.nextSetBit(i + 1), j++) {
      arr.write(j, i);
      if (i == Integer.MAX_VALUE) {
        break;
      }
    }
    long result = varsetFromArray(arr, size);
    UnmanagedMemory.free(arr);
    return result;
  }

  @CFunction("owl_sylvan_sat_one_bdd")
  static native long satOneBdd(long bdd);

  @CFunction("owl_sylvan_exists")
  static native long exists(long bdd, long vars);

  @CFunction("owl_sylvan_support")
  static native long support(long bdd);

  @CFunction("owl_sylvan_map_add")
  static native long mapAdd(long map, int var, long bdd);

  @CFunction("owl_sylvan_compose")
  static native long compose(long bdd, long map);

  @CStruct("owl_sylvan_protected_nodes_list")
  interface NodeList extends PointerBase {
    @CField("size")
    int getSize();

    @CField("size")
    void setSize(int size);

    @CField("list")
    CLongPointer getList();

    @CField("list")
    void setList(CLongPointer list);
  }

  @CEntryPoint(name = "owl_sylvan_get_referenced_nodes")
  static void getReferencedNodes(IsolateThread thread, NodeList nodeList) {
    var nodes = SylvanBddSetFactory.INSTANCE.getReferencedNodes();
    if (nodeList.getList().isNonNull()) {
      UnmanagedMemory.free(nodeList.getList());
    }
    nodeList.setSize(nodes.size());
    if (nodes.isEmpty()) {
      nodeList.setList(WordFactory.nullPointer());
    } else {
      CLongPointer list = UnmanagedMemory.malloc(8 * nodes.size());
      int ctr = 0;
      for (long node : nodes) {
        list.write(ctr++, node);
      }
      nodeList.setList(list);
    }
  }

  @CFunction("owl_sylvan_exchange_loop")
  static native void exchangeLoop(IsolateThread isolate);

  static class CDirectives implements CContext.Directives {

    @Override
    public List<String> getHeaderFiles() {
      // The header file with the C declarations that are imported.

      var headerLocation = System.getProperty("owlHeader");

      if (headerLocation == null) {
        throw new IllegalArgumentException("Location of header file is missing."
          + "Use -DowlHeader=/foo/bar/ to define location.");
      }

      return List.of(String.format("\"%s/owlsylvan.h\"", headerLocation));
    }

    @Override
    public List<String> getOptions() {
      return List.of("-L/usr/local/lib");
    }

    @Override
    public List<String> getLibraries() {
      return List.of("owlsylvan", "sylvan");
    }
  }
}
