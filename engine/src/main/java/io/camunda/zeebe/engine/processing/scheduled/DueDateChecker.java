/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.scheduled;

import io.camunda.zeebe.engine.api.ProcessingScheduleService;
import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.SimpleProcessingScheduleService.ScheduledTask;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.scheduler.clock.ActorClock;
import java.time.Duration;
import java.util.function.Function;

public final class DueDateChecker implements StreamProcessorLifecycleAware {
  private final boolean scheduleAsync;
  private final long timerResolution;
  private final Function<TaskResultBuilder, Long> visitor;

  private ScheduleDelayed scheduleService;

  /**
   * Indicates whether the checker should reschedule itself. Controlled by the stream processor's
   * lifecycle events, e.g. {@link #onPaused()} and {@link #onResumed()}.
   */
  private boolean shouldRescheduleChecker;

  /**
   * Keeps track of the next execution of the checker. It is set to null when the checker has no
   * need to reschedule itself anymore.
   */
  private NextExecution nextExecution = null;

  public DueDateChecker(
      final long timerResolution,
      final boolean scheduleAsync,
      final Function<TaskResultBuilder, Long> visitor) {
    this.timerResolution = timerResolution;
    this.scheduleAsync = scheduleAsync;
    this.visitor = visitor;
  }

  private TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    nextExecution = null;

    if (shouldRescheduleChecker) {
      final long nextDueDate = visitor.apply(taskResultBuilder);

      // reschedule the runnable if there are timers left

      if (nextDueDate > 0) {
        schedule(nextDueDate);
      }
    }

    return taskResultBuilder.build();
  }

  public void schedule(final long dueDate) {

    // We schedule only one runnable for all timers.
    // - The runnable is scheduled when the first timer is scheduled.
    // - If a new timer is scheduled which should be triggered before the current runnable is
    // executed then the runnable is canceled and re-scheduled with the new delay.
    // - Otherwise, we don't need to cancel the runnable. It will be rescheduled when it is
    // executed.

    if (!shouldRescheduleChecker) {
      return;
    }

    if (nextExecution == null || nextExecution.nextDueDate() - dueDate > timerResolution) {
      cancelNextExecution();
      final var delay = calculateDelayForNextRun(dueDate);
      final var task = scheduleService.runDelayed(delay, this::execute);
      nextExecution = new NextExecution(dueDate, task);
    }
  }

  private void cancelNextExecution() {
    if (nextExecution != null) {
      nextExecution.task().cancel();
      nextExecution = null;
    }
  }

  private void scheduleInitialExecution() {
    if (!shouldRescheduleChecker) {
      return;
    }

    // ensure that the checker is scheduled only once. No execution is expected, but we want to
    // cover all edge cases.
    cancelNextExecution();

    final var task = scheduleService.runDelayed(Duration.ZERO, this::execute);
    nextExecution = new NextExecution(-1, task);
  }

  /**
   * Calculates the delay for the next run so that it occurs at (or close to) due date. If due date
   * is in the future, the delay will be precise. If due date is in the past, now or in the very
   * near future, then a lower floor is applied to the delay. The lower floor is {@code
   * timerResolution}. This is to prevent the checker from being immediately rescheduled and thus
   * not giving any other tasks a chance to run.
   *
   * @param dueDate due date for which a scheduling delay is calculated
   * @return delay to hit the next due date; will be {@code >= timerResolution}
   */
  private Duration calculateDelayForNextRun(final long dueDate) {
    return Duration.ofMillis(Math.max(dueDate - ActorClock.currentTimeMillis(), timerResolution));
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    final var scheduleService = processingContext.getScheduleService();
    if (scheduleAsync) {
      this.scheduleService = scheduleService::runDelayedAsync;
    } else {
      this.scheduleService = scheduleService::runDelayed;
    }

    shouldRescheduleChecker = true;
    scheduleInitialExecution();
  }

  @Override
  public void onClose() {
    shouldRescheduleChecker = false;
    cancelNextExecution();
  }

  @Override
  public void onFailed() {
    shouldRescheduleChecker = false;
    cancelNextExecution();
  }

  @Override
  public void onPaused() {
    shouldRescheduleChecker = false;
    cancelNextExecution();
  }

  @Override
  public void onResumed() {
    shouldRescheduleChecker = true;
    scheduleInitialExecution();
  }

  /**
   * Keeps track of the next execution of the checker.
   *
   * @param nextDueDate The due date of the next timer to be checked or -1 on the first execution,
   *     i.e. we don't know the next due date yet.
   * @param task The scheduled task for the next execution, can be used for canceling the task.
   */
  private record NextExecution(long nextDueDate, ScheduledTask task) {}

  /** Abstracts over async and sync scheduling methods of {@link ProcessingScheduleService}. */
  @FunctionalInterface
  interface ScheduleDelayed {
    /**
     * Implemented by either {@link ProcessingScheduleService#runDelayed(Duration, Task)} or {@link
     * ProcessingScheduleService#runDelayedAsync(Duration, Task)}
     */
    ScheduledTask runDelayed(final Duration delay, final Task task);
  }
}
