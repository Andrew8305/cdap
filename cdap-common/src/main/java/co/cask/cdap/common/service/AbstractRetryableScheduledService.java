/*
 * Copyright © 2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.common.service;

import co.cask.cdap.api.retry.RetriesExhaustedException;
import com.google.common.util.concurrent.AbstractScheduledService;
import org.apache.twill.common.Threads;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * A {@link AbstractScheduledService} that runs task periodically and with retry logic upon task failure.
 */
public abstract class AbstractRetryableScheduledService extends AbstractScheduledService {

  private final RetryStrategy retryStrategy;

  private long delayMillis;
  private int failureCount;
  private long nonFailureStartTime;
  private ScheduledExecutorService executor;

  /**
   * Constructor.
   *
   * @param retryStrategy the {@link RetryStrategy} for determining how to retry when there is exception raised
   */
  protected AbstractRetryableScheduledService(RetryStrategy retryStrategy) {
    this.retryStrategy = retryStrategy;
  }

  /**
   * Runs the task in one scheduled iteration.
   *
   * @return the number of milliseconds to delay until the next call to this method
   * @throws Exception if the task failed
   */
  protected abstract long runTask() throws Exception;

  /**
   * Determines if retry on the given {@link Exception}.
   *
   * @param ex the exception raised by the {@link #runTask()} call.
   * @return {@code true} to retry in the next iteration; otherwise {@code false} to fail and terminate this service
   *         with the given exception.
   */
  protected boolean shouldRetry(Exception ex) {
    return true;
  }

  /**
   * Performs startup task. This method will be called from the executor returned by the {@link #executor()} method.
   * By default this method does nothing.
   *
   * @throws Exception if startup of this service failed
   */
  protected void doStartUp() throws Exception {
    // No-op
  }

  /**
   * Performs shutdown task. This method will be called from the executor returned by the {@link #executor()} method.
   * By default this method does nothing.
   *
   * @throws Exception if shutdown of this service failed
   */
  protected void doShutdown() throws Exception {
    // No-op
  }

  /**
   * Returns the name of this service.
   */
  protected String getServiceName() {
    return getClass().getSimpleName();
  }

  @Override
  protected ScheduledExecutorService executor() {
    executor = Executors.newSingleThreadScheduledExecutor(Threads.createDaemonThreadFactory(getServiceName()));
    return executor;
  }

  @Override
  protected final void startUp() throws Exception {
    doStartUp();
  }

  @Override
  protected final void shutDown() throws Exception {
    try {
      doShutdown();
    } finally {
      if (executor != null) {
        executor.shutdown();
      }
    }
  }

  @Override
  protected final void runOneIteration() throws Exception {
    try {
      if (nonFailureStartTime == 0L) {
        nonFailureStartTime = System.currentTimeMillis();
      }

      delayMillis = runTask();
      nonFailureStartTime = 0L;
      failureCount = 0;
    } catch (Exception e) {
      if (!shouldRetry(e)) {
        throw e;
      }

      long delayMillis = retryStrategy.nextRetry(++failureCount, nonFailureStartTime);
      if (delayMillis < 0) {
        e.addSuppressed(new RetriesExhaustedException(String.format("Retries exhausted after %d failures and %d ms.",
                                                                    failureCount,
                                                                    System.currentTimeMillis() - nonFailureStartTime)));
        throw e;
      }
      this.delayMillis = delayMillis;
    }
  }

  @Override
  protected final Scheduler scheduler() {
    return new CustomScheduler() {
      @Override
      protected Schedule getNextSchedule() {
        return new Schedule(delayMillis, TimeUnit.MILLISECONDS);
      }
    };
  }
}
