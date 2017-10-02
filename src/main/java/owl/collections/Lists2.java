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

package owl.collections;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

public final class Lists2 {
  private Lists2() {
  }

  public static <T> boolean addAllDistinct(List<T> list, Collection<T> elements) {
    boolean changed = false;

    for (T element : elements) {
      changed |= addDistinct(list, element);
    }

    return changed;
  }

  public static <T> boolean addDistinct(List<T> list, T element) {
    if (list.contains(element)) {
      return false;
    }

    list.add(element);
    return true;
  }

  public static <T> void forEachIndexed(List<T> list, IndexedConsumer<T> action) {
    ListIterator<T> iterator = list.listIterator();
    while (iterator.hasNext()) {
      action.accept(iterator.nextIndex(), iterator.next());
    }
  }

  @FunctionalInterface
  public interface IndexedConsumer<T> {
    void accept(int index, T element);
  }
}
