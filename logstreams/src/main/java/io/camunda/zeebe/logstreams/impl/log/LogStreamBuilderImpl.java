/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.logstreams.impl.log;

import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import java.util.Objects;

public final class LogStreamBuilderImpl implements LogStreamBuilder {
  private static final int MINIMUM_FRAGMENT_SIZE = 4 * 1024;
  private int maxFragmentSize = 1024 * 1024 * 4;
  private int partitionId = -1;
  private LogStorage logStorage;
  private String logName;

  @Override
  public LogStreamBuilder withMaxFragmentSize(final int maxFragmentSize) {
    this.maxFragmentSize = maxFragmentSize;
    return this;
  }

  @Override
  public LogStreamBuilder withLogStorage(final LogStorage logStorage) {
    this.logStorage = logStorage;
    return this;
  }

  @Override
  public LogStreamBuilder withPartitionId(final int partitionId) {
    this.partitionId = partitionId;
    return this;
  }

  @Override
  public LogStreamBuilder withLogName(final String logName) {
    this.logName = logName;
    return this;
  }

  @Override
  public LogStream build() {
    validate();
    return new LogStreamImpl(logName, partitionId, maxFragmentSize, logStorage);
  }

  private void validate() {
    Objects.requireNonNull(logStorage, "Must specify a log storage");

    if (maxFragmentSize < MINIMUM_FRAGMENT_SIZE) {
      throw new IllegalArgumentException(
          String.format(
              "Expected fragment size to be at least '%d', but was '%d'",
              MINIMUM_FRAGMENT_SIZE, maxFragmentSize));
    }
  }
}
