/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.startup;

import static java.util.Collections.singletonList;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"rawtypes", "unchecked"})
class StartupProcessTest {

  private static final Object STARTUP_CONTEXT =
      new Object() {
        @Override
        public String toString() {
          return "startupContext";
        }
      };

  private static final Object SHUTDOWN_CONTEXT =
      new Object() {
        @Override
        public String toString() {
          return "shutdownContext";
        }
      };

  static final class InvocationCountingStartupStep implements StartupStep<Object> {

    private int startupInvocationCounter = 0;
    private int shutdownInvocationCounter = 0;

    int getStartupInvocationCounter() {
      return startupInvocationCounter;
    }

    int getShutdownInvocationCounter() {
      return shutdownInvocationCounter;
    }

    @Override
    public String getName() {
      return "InvocationCountingStartupStep";
    }

    @Override
    public CompletableFuture<Object> startup(final Object o) {
      startupInvocationCounter++;
      return completedFuture(o);
    }

    @Override
    public CompletableFuture<Object> shutdown(final Object o) {
      shutdownInvocationCounter++;
      return completedFuture(o);
    }
  }

  static final class WaitingStartupStep implements StartupStep<Object> {

    private final CountDownLatch startupCountdownLatch;
    private final boolean completeWithException;

    WaitingStartupStep(
        final CountDownLatch startupCountdownLatch, final boolean completeWithException) {
      this.startupCountdownLatch = startupCountdownLatch;
      this.completeWithException = completeWithException;
    }

    @Override
    public String getName() {
      return "WaitingStartupStep";
    }

    @Override
    public CompletableFuture<Object> startup(final Object o) {
      final var startupFuture = new CompletableFuture<>();
      final var startupThread =
          new Thread(
              () -> {
                try {
                  startupCountdownLatch.await();
                } catch (final InterruptedException e) {
                  e.printStackTrace();
                } finally {
                  if (!completeWithException) {
                    startupFuture.complete(o);
                  } else {
                    startupFuture.completeExceptionally(new Throwable("completed exceptionally"));
                  }
                }
              });
      startupThread.start();
      return startupFuture;
    }

    @Override
    public CompletableFuture<Object> shutdown(final Object o) {
      return completedFuture(o);
    }
  }

  @Nested
  class MainUseCase {

    private static final String INPUT_STEP1 = "inputStep1";
    private static final String INPUT_STEP2 = "inputStep2";
    private static final String RESULT_STEP1 = "resultStep1";
    private static final String RESULT_STEP2 = "resultStep2";

    private final Exception testException1 = new Exception("TEST_EXCEPTION1");
    private final Exception testException2 = new Exception("TEST_EXCEPTION1");

    private StartupStep mockStep1;
    private StartupStep mockStep2;

    @BeforeEach
    void setup() {
      mockStep1 = mock(StartupStep.class);
      mockStep2 = mock(StartupStep.class);

      when(mockStep1.getName()).thenReturn("step1");
      when(mockStep2.getName()).thenReturn("step2");
    }

    @Test
    void shouldCallStartupStepsInOrder() {
      // given
      when(mockStep1.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));
      when(mockStep2.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));

      // when
      sut.startup(STARTUP_CONTEXT).join();

      // then
      final var invocationRecorder = inOrder(mockStep1, mockStep2);
      invocationRecorder.verify(mockStep1).startup(STARTUP_CONTEXT);
      invocationRecorder.verify(mockStep2).startup(STARTUP_CONTEXT);
    }

    @Test
    void shouldCallShutdownStepsInReverseOrder() {
      // given
      when(mockStep1.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));
      when(mockStep1.shutdown(SHUTDOWN_CONTEXT)).thenReturn(completedFuture(SHUTDOWN_CONTEXT));
      when(mockStep2.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));
      when(mockStep2.shutdown(SHUTDOWN_CONTEXT)).thenReturn(completedFuture(SHUTDOWN_CONTEXT));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));
      sut.startup(STARTUP_CONTEXT).join();

      // when
      sut.shutdown(SHUTDOWN_CONTEXT).join();

      // then
      final var invocationRecorder = inOrder(mockStep1, mockStep2);
      invocationRecorder.verify(mockStep2).shutdown(SHUTDOWN_CONTEXT);
      invocationRecorder.verify(mockStep1).shutdown(SHUTDOWN_CONTEXT);
    }

    @Test
    void shouldCallSubsequentStartupStepWithResultOfPreviousStep() {
      // given
      when(mockStep1.startup(INPUT_STEP1)).thenReturn(completedFuture(RESULT_STEP1));
      when(mockStep2.startup(RESULT_STEP1)).thenReturn(completedFuture(RESULT_STEP2));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));

      // when
      final var actualResult = sut.startup(INPUT_STEP1).join();

      // then
      final var invocationRecorder = inOrder(mockStep1, mockStep2);
      invocationRecorder.verify(mockStep1).startup(INPUT_STEP1);
      invocationRecorder.verify(mockStep2).startup(RESULT_STEP1);

      assertThat(actualResult).isSameAs(RESULT_STEP2);
    }

    @Test
    void shouldCallSubsequentShutdownStepWithResultOfPreviousStep() {
      // given
      when(mockStep1.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));
      when(mockStep2.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));

      when(mockStep2.shutdown(INPUT_STEP2)).thenReturn(completedFuture(RESULT_STEP2));
      when(mockStep1.shutdown(RESULT_STEP2)).thenReturn(completedFuture(RESULT_STEP1));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));
      sut.startup(STARTUP_CONTEXT).join();

      // when
      final var actualResult = sut.shutdown(INPUT_STEP2).join();

      // then
      final var invocationRecorder = inOrder(mockStep1, mockStep2);
      invocationRecorder.verify(mockStep2).shutdown(INPUT_STEP2);
      invocationRecorder.verify(mockStep1).shutdown(RESULT_STEP2);

      assertThat(actualResult).isSameAs(RESULT_STEP1);
    }

    @Test
    void shouldAbortStartupIfOneStepThrewAnException() {
      // given
      final var testException = new Exception("TEST_EXCEPTION");

      when(mockStep1.startup(STARTUP_CONTEXT)).thenReturn(failedFuture(testException));
      when(mockStep2.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));

      // when
      final var actualResult = sut.startup(STARTUP_CONTEXT);

      // then
      verify(mockStep2, never()).startup(STARTUP_CONTEXT);

      assertThat(actualResult).isCompletedExceptionally();

      Assertions.assertThatThrownBy(actualResult::join)
          .isInstanceOf(CompletionException.class)
          .hasCause(testException);
    }

    @Test
    void shouldContinueShutdownEvenIfStepsThrowExceptions() {
      // given
      when(mockStep1.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));
      when(mockStep2.startup(STARTUP_CONTEXT)).thenReturn(completedFuture(STARTUP_CONTEXT));

      when(mockStep1.shutdown(SHUTDOWN_CONTEXT)).thenReturn(failedFuture(testException1));
      when(mockStep2.shutdown(SHUTDOWN_CONTEXT)).thenReturn(failedFuture(testException2));

      final var sut = new StartupProcess<>(List.of(mockStep1, mockStep2));
      sut.startup(STARTUP_CONTEXT).join();

      // when
      final var actualResult = sut.shutdown(SHUTDOWN_CONTEXT);

      // then
      verify(mockStep2).shutdown(SHUTDOWN_CONTEXT);
      verify(mockStep1).shutdown(SHUTDOWN_CONTEXT);

      assertThat(actualResult).isCompletedExceptionally();

      Assertions.assertThatThrownBy(actualResult::join)
          .isInstanceOf(CompletionException.class)
          .getCause()
          .hasSuppressedException(testException1)
          .hasSuppressedException(testException2);
    }

    @Test
    void shouldAbortOngoingStartupWhenShutdownIsCalled() {
      // given
      final var step1CountdownLatch = new CountDownLatch(1);
      final var step1 = new WaitingStartupStep(step1CountdownLatch, false);

      final var sut = new StartupProcess<>(List.of(step1, mockStep2));

      // when
      final var startupFuture = sut.startup(STARTUP_CONTEXT);
      final var shutdownFuture = sut.shutdown(SHUTDOWN_CONTEXT);

      step1CountdownLatch.countDown();

      // then
      verifyNoInteractions(mockStep2);

      await().until(startupFuture::isDone);
      await().until(shutdownFuture::isDone);

      assertThat(startupFuture).isCompletedExceptionally();
      assertThat(shutdownFuture).isCompletedWithValue(SHUTDOWN_CONTEXT);
    }
  }

  @Nested
  class IllegalStatesAndArguments {

    @Test
    void shouldThrowNPEWhenCalledWithNoSteps() {
      // when + then
      assertThatThrownBy(() -> new StartupProcess<>(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowNPEWhenCalledWithNoLogger() {
      // when + then
      assertThatThrownBy(() -> new StartupProcess<>(null, Collections.emptyList()))
          .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldThrowIllegalStateIfStartupIsCalledMoreThanOnce() {
      // given
      final var sut = new StartupProcess<>(Collections.emptyList());

      // when + then
      sut.startup(STARTUP_CONTEXT).join();

      assertThatThrownBy(() -> sut.startup(STARTUP_CONTEXT))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("startup(...) must only be called once");
    }

    @Test
    void shouldPerformShutdownOnlyOnceIfShutdownIsCalledMultipleTimes() {
      // given
      final var step = new InvocationCountingStartupStep();
      final var sut = new StartupProcess<>(singletonList(step));

      // when
      sut.startup(STARTUP_CONTEXT).join();
      sut.shutdown(SHUTDOWN_CONTEXT).join();
      sut.shutdown(SHUTDOWN_CONTEXT).join();

      // then
      assertThat(step.getShutdownInvocationCounter()).isEqualTo(1);
    }
  }

  @Nested
  class EmptyList {

    private final StartupProcess<Object> sut = new StartupProcess<>(Collections.emptyList());

    @Test
    void shouldReturnContextImmediatelyOnStartup() {
      // when
      final var startupFuture = sut.startup(STARTUP_CONTEXT);

      // then
      assertThat(startupFuture).isCompletedWithValue(STARTUP_CONTEXT);
    }

    @Test
    void shouldReturnContextImmediatelyOnShutdown() {
      // given
      sut.startup(STARTUP_CONTEXT).join();

      // when
      final var shutdownFuture = sut.shutdown(SHUTDOWN_CONTEXT);

      // then
      assertThat(shutdownFuture).isCompletedWithValue(SHUTDOWN_CONTEXT);
    }
  }
}
