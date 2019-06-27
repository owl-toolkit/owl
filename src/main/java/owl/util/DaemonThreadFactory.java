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

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class DaemonThreadFactory implements ThreadFactory {
  private static final AtomicInteger factoryNumber = new AtomicInteger(1);

  private final ThreadGroup group;
  private final AtomicInteger threadNumber = new AtomicInteger(1);
  private final int number = factoryNumber.getAndIncrement();

  public DaemonThreadFactory(ThreadGroup group) {
    this.group = group;
  }

  @Override
  public Thread newThread(Runnable r) {
    String name = String.format("owl-worker-%d-%d", number, threadNumber.getAndIncrement());
    Thread t = new Thread(group, r, name);
    t.setDaemon(true);
    return t;
  }
}
