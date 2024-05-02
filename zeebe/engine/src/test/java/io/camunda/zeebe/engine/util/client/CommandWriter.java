/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

public interface CommandWriter {

  long writeCommand(final Intent intent, final UnifiedRecordValue recordValue);

  long writeCommand(
      final Intent intent, final UnifiedRecordValue recordValue, String... authorizedTenants);

  long writeCommand(long key, Intent intent, UnifiedRecordValue recordValue);

  long writeCommand(
      final long key,
      final Intent intent,
      final UnifiedRecordValue recordValue,
      final String... authorizedTenants);

  long writeCommand(
      final int requestStreamId,
      final long requestId,
      final Intent intent,
      final UnifiedRecordValue value);

  long writeCommandOnPartition(
      final int partitionId, final Intent intent, final UnifiedRecordValue recordValue);

  long writeCommandOnPartition(
      int partitionId, long key, Intent intent, UnifiedRecordValue recordValue);

  long writeCommandOnPartition(
      int partitionId,
      long key,
      Intent intent,
      UnifiedRecordValue recordValue,
      final String... authorizedTenants);
}
