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

package owl.util;

import static org.junit.jupiter.api.Assertions.fail;

import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public final class Assertions {
  private Assertions() {
  }

  public static <T> void assertThat(@Nullable T actual, Predicate<T> expected) {
    assertThat(actual, expected, "Test failed");
  }

  public static <T> void assertThat(@Nullable T actual, Predicate<T> expected, String message) {
    assertThat((Supplier<T>) () -> actual, expected, message);
  }

  public static <T> void assertThat(Supplier<T> actual, Predicate<T> expected) {
    assertThat(actual, expected, "Test failed");
  }

  public static <T> void assertThat(Supplier<T> actual, Predicate<T> expected, String message) {
    if (!expected.test(actual.get())) {
      fail(message);
    }
  }
}
