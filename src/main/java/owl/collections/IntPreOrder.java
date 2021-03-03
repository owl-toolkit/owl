/*
 * Copyright (C) 2017 Tobias Meggendorfer
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

package owl.collections;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class represents a total pre-orders of {@code {1,..n}}, which are identified by a list of
 * their equivalence classes.
 *
 * <p>This domain has a canonical lattice structure induced by the "granularity" - a total pre-order
 * refines another if it induces smaller equivalence classes. The top element is the most granular
 * pre-order, whose only equivalence class is the full domain. The bottom elements are all
 * total orders (i.e. they also are antisymmetric).</p>
 *
 * <p>The class provides two operations:
 * <ul>
 * <li>{@link #generation}: Computes the record obtained by "rebirth" of the given
 * elements, i.e. they are defined as the new first equivalence class.
 * </li>
 * <li>{@link #refines(IntPreOrder)}: Determines whether this record is strict refinement of the
 * other record.
 * </li>
 * </ul>
 *
 * <p>This class is imported from <a href="https://github.com/incaseoftrouble/naturals-util">
 * https://github.com/incaseoftrouble/naturals-util</a>
 */
@SuppressWarnings("PMD")
public class IntPreOrder {
  private static final IntPreOrder EMPTY = finest(0);

  /* The array used to store the equivalence classes of the pre-order. Each class is stored as a
   * sorted array. */
  protected final int[][] array;
  /* Cached size of the domain */
  protected final int size;
  /* Cached hash code */
  private final int hashCode;

  /**
   * Constructs a record from the given array. Should not be called directly. Instead, use
   * {@link #coarsest(int)} and {@link #finest(int)}.
   *
   * @param array
   *     The array specifying the equivalence classes
   */
  // Visible for testing
  IntPreOrder(int[][] array) {
    assert isWellFormed(array);
    //noinspection AssignmentToCollectionOrArrayFieldFromParameter
    this.array = array;
    int length = 0;
    for (int[] partition : array) {
      length += partition.length;
    }
    this.size = length;
    this.hashCode = Arrays.deepHashCode(array);
  }

  /**
   * Returns the coarsest pre-order over the {@code {1,..,n}} domain, i.e. {@code [{1,..,n}]}.
   *
   * @param n
   *     The size of the domain
   *
   * @return The coarsest record over the domain
   */
  public static IntPreOrder coarsest(int n) {
    int[][] array = new int[1][n];
    for (int i = 0; i < n; i++) {
      array[0][i] = i;
    }
    return new IntPreOrder(array);
  }

  /**
   * Returns the empty pre-order.
   */
  public static IntPreOrder empty() {
    return EMPTY;
  }

  /**
   * Returns a finest pre-order over the {@code {1,..,n}} domain, i.e. a record of the form
   * {@code [{1},{2},..,{n}]}.
   */
  public static IntPreOrder finest(int n) {
    int[][] array = new int[n][1];
    for (int i = 0; i < n; i++) {
      array[i][0] = i;
    }
    return new IntPreOrder(array);
  }

  private static boolean isWellFormed(int[][] array) {
    // Check that the given array actually is an element of the domain

    int length = 0;
    int distinctValues = 0;

    Set<Integer> values = new HashSet<>();
    for (int[] equivClass : array) {
      // Each equivalence class must not be empty
      assert equivClass.length > 0 : "Empty partition";

      length += equivClass.length;

      // Each set has to be a true set (no duplicate values)
      Set<Integer> classValues = IntStream.of(equivClass)
        .boxed().collect(Collectors.toUnmodifiableSet());
      assert classValues.size() == equivClass.length :
        String.format("%s has duplicate values", Arrays.toString(equivClass));

      // Each set has to be disjoint from all other elements of the partition
      distinctValues += classValues.size();
      values.addAll(classValues);
      assert values.size() == distinctValues :
        String.format("%s has values in previous classes", Arrays.toString(equivClass));

      // The elements have to be sorted (this is an implementation invariant)
      int value = equivClass[0];
      for (int i = 1; i < equivClass.length; i++) {
        assert equivClass[i] > value :
          String.format("%s not sorted", Arrays.toString(equivClass));
        value = equivClass[i];
      }
    }

    // This should always be true due to the above asserts
    assert distinctValues == length;

    // The sets have to form a true partition
    assert values.equals(
      IntStream.range(0, distinctValues).boxed().collect(Collectors.toUnmodifiableSet())) :
      String.format("Missing values %s", values);
    return true;
  }

  private static String toString(int[][] array) {
    if (array.length == 0) {
      return "[]";
    }

    StringBuilder builder = new StringBuilder(2 + array.length * 4);
    builder.append('[');
    for (int[] partition : array) {
      assert partition.length > 0;
      builder.append('{').append(partition[0]);
      for (int i = 1; i < partition.length; i++) {
        builder.append(',').append(partition[i]);
      }
      builder.append('}');
    }
    builder.append(']');

    return builder.toString();
  }

  /**
   * Returns the number of classes.
   */
  public int classes() {
    return array.length;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IntPreOrder)) {
      return false;
    }

    IntPreOrder record = (IntPreOrder) o;
    return size == record.size
      && hashCode == record.hashCode
      && Arrays.deepEquals(array, record.array);
  }

  /**
   * Returns the class with the given {@code index}.
   * <p><strong>Warning</strong>: For performance reasons, the internal array is returned (instead
   * of a copy). This array must not be modified.</p>
   */
  public int[] equivalenceClass(int index) {
    return array[index];
  }

  private boolean inDomain(int i) {
    return 0 <= i && i < size;
  }

  /**
   * Computes the pre-order obtained by declaring {@code newborn} as the smallest elements.
   *
   * @param newborn
   *     The new smallest elements.
   *
   * @return The pre-order with {@code newborn} as new smallest elements.
   */
  public IntPreOrder generation(Set<Integer> newborn) {
    if (newborn.isEmpty()) {
      return this;
    }
    // Can't rebirth what is not existing
    assert newborn.stream().allMatch(this::inDomain);

    int newbornCount = newborn.size();
    if (newbornCount == size) {
      if (array.length == 1) {
        return this;
      }
      // Whole domain is reborn - the returned set is the coarsest element
      return coarsest(size);
    }

    int[] newbornArray = newborn.stream().mapToInt(x -> x).toArray();
    Arrays.sort(newbornArray);

    if (newbornCount == size - 1) {
      // All but one element is reborn - this uniquely defines the order

      // Find the one element which is not reborn
      int senior = -1;
      for (int i = 0; i < size; i++) {
        if (!newborn.contains(i)) {
          senior = i;
          break;
        }
      }
      assert senior != -1;

      return new IntPreOrder(new int[][] {newbornArray, {senior}});
    }

    // General case

    // Don't know how many classes there will be - allocate worst case and trim afterwards
    int[][] newArrayTmp = new int[array.length + 1][];
    // The newborns form the first class
    newArrayTmp[0] = newbornArray;

    // Copy all elements which are not reborn in the corresponding classes
    int newClassIndex = 1;
    int foundReborn = 0;
    for (int[] equivalenceClass : array) {
      if (newbornCount == foundReborn) {
        // The equivalence classes are immutable - we can share common classes
        newArrayTmp[newClassIndex] = equivalenceClass;
        newClassIndex += 1;
        continue;
      }
      assert equivalenceClass.length > 0;

      // Same as before - we don't know how many elements the new class will have - but at most
      // as much as before
      int newClassSize = 0;
      int[] newClassElements = new int[equivalenceClass.length];
      for (int value : equivalenceClass) {
        if (Arrays.binarySearch(newbornArray, value) >= 0) {
          foundReborn += 1;
          continue;
        }
        newClassElements[newClassSize] = value;
        newClassSize += 1;
      }

      if (newClassSize > 0) {
        // == 0 if all the elements of this class were reborn

        newArrayTmp[newClassIndex] = newClassSize < newClassElements.length
          // Some elements were reborn - trim the class array
          ? Arrays.copyOf(newClassElements, newClassSize)
          // No elements were touched - we can use the current class as is
          : equivalenceClass;
        newClassIndex += 1;
      }
    }

    int[][] newArray = newClassIndex < newArrayTmp.length
      // Some classes disappeared - trim the overall array
      ? Arrays.copyOf(newArrayTmp, newClassIndex)
      // No classes were emptied - we can keep the array as is
      : newArrayTmp;
    return new IntPreOrder(newArray);
  }

  @Override
  public int hashCode() {
    assert Arrays.deepHashCode(array) == hashCode : "Array was modified";
    return hashCode;
  }

  /**
   * Determines whether the pre-order defined by this object <strong>strictly</strong> refines the
   * {@code other}.
   */
  @SuppressWarnings("ReferenceEquality")
  public boolean refines(IntPreOrder other) {
    //noinspection ObjectEquality
    if (this == other) {
      // We only want strict refinement
      return false;
    }

    // Can't compare different domains
    assert size == other.size;
    int classCount = array.length;
    int otherClassCount = other.array.length;

    if (classCount <= otherClassCount) {
      // We have less classes - certainly can't refine
      return false;
    }

    // As a first pass, we try to match the lengths of the classes (this is faster than searching
    // through the values). More precisely, for each class in the other order, we try to find one
    // or more exactly fitting in it (in terms of their size). These results are then cached into
    // the otherClassByLength array - for each class index, we store which class in the other order
    // it should belong to.
    int[] otherClassByLength = new int[classCount];

    {
      int otherClassIndex = 0;
      int otherClassSize = other.array[otherClassIndex].length;
      int coveredValues = 0;
      for (int classIndex = 0; classIndex < classCount; classIndex++) {
        if (coveredValues == otherClassSize) {
          // We filled one class of the other order exactly - move to the next
          otherClassIndex += 1;
          otherClassSize = other.array[otherClassIndex].length;
          coveredValues = 0;
        }
        int classSize = array[classIndex].length;
        coveredValues += classSize;
        if (coveredValues > otherClassSize) {
          // There is no way this order could refine the other since the lengths don't add up
          return false;
        }
        otherClassByLength[classIndex] = otherClassIndex;
      }
    }

    // Now go over all classes of the other and check that the classes which should be contained in
    // it (by size) exactly contain the same values
    int classIndex = 0;
    for (int otherClassIndex = 0; otherClassIndex < otherClassCount; otherClassIndex++) {
      // Check if there is a 1-1 matching in terms of lengths
      if (array[classIndex].length == other.array[otherClassIndex].length) {
        // Check if the classes are equal - since they are stored sorted, a simple array comparison
        // is sufficient
        if (!Arrays.equals(array[classIndex], other.array[otherClassIndex])) {
          return false;
        }
        classIndex += 1;
        continue;
      }

      // Since we known that the union of the classes we try to fit in the other is of the same
      // length, we can just check if each element of all the classes is contained in the other
      // class. Also, since the classes are stored sorted, we can use binary search for the lookup.
      while (classIndex < classCount && otherClassByLength[classIndex] == otherClassIndex) {
        for (int value : array[classIndex]) {
          if (Arrays.binarySearch(other.array[otherClassIndex], value) < 0) {
            return false;
          }
        }
        classIndex += 1;
      }
    }
    return true;
  }

  /**
   * Returns the domain size.
   */
  public int size() {
    return size;
  }

  @Override
  public String toString() {
    return toString(array);
  }
}
