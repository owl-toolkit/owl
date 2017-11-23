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
