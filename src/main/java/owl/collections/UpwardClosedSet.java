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

package owl.collections;

import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.stream.Collectors;

/**
 * Bucket-based implementation of an upward-closed set.
 */
public final class UpwardClosedSet {
  private static final long[] EMPTY_ARRAY = new long[0];
  private static final long[][] EMPTY_BUCKETS = {};

  private static final UpwardClosedSet EMPTY = new UpwardClosedSet();
  private static final UpwardClosedSet UNIVERSE = new UpwardClosedSet(0);

  private final long[][] buckets;

  private UpwardClosedSet() {
    this.buckets = EMPTY_BUCKETS;
    checkInvariants();
  }

  private UpwardClosedSet(long element) {
    int i = Long.bitCount(element);
    this.buckets = new long[i + 1][];
    Arrays.fill(buckets, EMPTY_ARRAY);
    this.buckets[i] = new long[] {element};
    checkInvariants();
  }

  private UpwardClosedSet(LongList[] buckets) {
    int size = buckets.length;

    while (0 < size && buckets[size - 1] == null) {
      size--;
    }

    this.buckets = new long[size][];
    Arrays.setAll(this.buckets, i -> {
      var bucket = buckets[i];
      return bucket == null ? EMPTY_ARRAY : bucket.toLongArray();
    });

    checkInvariants();
  }

  public static UpwardClosedSet of() {
    return EMPTY;
  }

  public static UpwardClosedSet of(BitSet bitSet) {
    Preconditions.checkArgument(bitSet.length() <= Long.SIZE);

    if (bitSet.isEmpty()) {
      return UNIVERSE;
    }

    return new UpwardClosedSet(bitSet.toLongArray()[0]);
  }

  public boolean contains(BitSet bitSet) {
    Preconditions.checkArgument(bitSet.length() <= Long.SIZE);
    long element = bitSet.isEmpty() ? 0 : bitSet.toLongArray()[0];

    for (long[] bucket : buckets) {
      for (long set : bucket) {
        if ((element | set) == element) {
          return true;
        }
      }
    }

    return false;
  }

  public UpwardClosedSet intersection(UpwardClosedSet otherSet) {
    LongList[] newBuckets = new LongList[this.buckets.length * otherSet.buckets.length];
    LongSet seenElements = new LongOpenHashSet();

    for (long[] bucket1 : buckets) {
      for (long[] bucket2 : otherSet.buckets) {
        for (long element1 : bucket1) {
          for (long element2 : bucket2) {
            long intersection = element1 | element2;

            if (seenElements.add(intersection)) {
              insert(newBuckets, intersection);
            }
          }
        }
      }
    }

    return new UpwardClosedSet(newBuckets);
  }

  public List<BitSet> representatives() {
    return Arrays.stream(buckets)
      .flatMapToLong(Arrays::stream)
      .mapToObj(x -> BitSet.valueOf(new long[]{x}))
      .collect(Collectors.toUnmodifiableList());
  }

  public UpwardClosedSet union(UpwardClosedSet otherSet) {
    if (otherSet.buckets.length > this.buckets.length) {
      return otherSet.union(this);
    }

    LongList[] newBuckets = new LongList[this.buckets.length];
    LongSet seenElements = new LongOpenHashSet();

    Arrays.setAll(newBuckets, i -> {
      var bucket = new LongArrayList(this.buckets[i]);
      seenElements.addAll(bucket);
      return bucket;
    });

    for (long[] bucket : otherSet.buckets) {
      for (long element : bucket) {
        if (!seenElements.contains(element)) {
          insert(newBuckets, element);
        }
      }
    }

    return new UpwardClosedSet(newBuckets);
  }

  private void checkInvariants() {
    for (int i = 0; i < buckets.length; i++) {
      for (long element : buckets[i]) {
        assert Long.bitCount(element) == i;
      }
    }
  }

  private static void insert(LongList[] buckets, long newElement) {
    int bitCount = Long.bitCount(newElement);

    for (int i = 0; i < bitCount; i++) {
      var bucket = buckets[i];

      if (bucket != null) {
        for (PrimitiveIterator.OfLong iterator = bucket.iterator(); iterator.hasNext(); ) {
          long oldElement = iterator.nextLong();

          if ((oldElement & newElement) == oldElement) {
            return;
          }
        }
      }
    }

    for (int i = bitCount + 1; i < buckets.length; i++) {
      var bucket = buckets[i];

      if (bucket != null) {
        for (PrimitiveIterator.OfLong iterator = bucket.iterator(); iterator.hasNext(); ) {
          long oldElement = iterator.nextLong();

          if ((oldElement & newElement) == newElement) {
            iterator.remove();
          }
        }
      }
    }

    var bucket = buckets[bitCount];

    if (bucket == null) {
      bucket = new LongArrayList();
      bucket.add(newElement);
      buckets[bitCount] = bucket;
    } else {
      bucket.add(newElement);
    }
  }
}

