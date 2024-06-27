/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static io.camunda.optimize.AbstractIT.OPENSEARCH_PASSING;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.AbstractPlatformIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag(OPENSEARCH_PASSING)
public class UserTaskIdentityCacheServiceSchedulerIT extends AbstractPlatformIT {

  @Test
  public void verifySyncEnabledByDefault() {
    assertThat(getIdentityCacheService().isScheduledToRun()).isTrue();
  }

  @Test
  public void testSyncStoppedSuccessfully() {
    try {
      getIdentityCacheService().stopScheduledSync();
      assertThat(getIdentityCacheService().isScheduledToRun()).isFalse();
    } finally {
      getIdentityCacheService().startScheduledSync();
    }
  }

  private PlatformUserTaskIdentityCache getIdentityCacheService() {
    return embeddedOptimizeExtension.getUserTaskIdentityCache();
  }
}