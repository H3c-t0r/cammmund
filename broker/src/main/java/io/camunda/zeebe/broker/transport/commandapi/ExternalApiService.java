/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.transport.commandapi;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.PartitionListener;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageListener;
import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.backpressure.RequestLimiter;
import io.camunda.zeebe.broker.transport.queryapi.QueryApiRequestHandler;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.transport.ServerTransport;
import io.camunda.zeebe.util.sched.Actor;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import java.util.function.Consumer;
import org.agrona.collections.IntHashSet;

public final class ExternalApiService extends Actor
    implements PartitionListener, DiskSpaceUsageListener {

  private final PartitionAwareRequestLimiter limiter;
  private final ServerTransport serverTransport;
  private final CommandApiRequestHandler commandRequestHandler;
  private final QueryApiRequestHandler queryRequestHandler;
  private final IntHashSet leadPartitions = new IntHashSet();
  private final String actorName;

  public ExternalApiService(
      final ServerTransport serverTransport,
      final BrokerInfo localBroker,
      final PartitionAwareRequestLimiter limiter) {
    this.serverTransport = serverTransport;
    this.limiter = limiter;
    commandRequestHandler = new CommandApiRequestHandler();
    queryRequestHandler = new QueryApiRequestHandler(serverTransport);
    actorName = buildActorName(localBroker.getNodeId(), "ExternalApiService");
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorClosing() {
    for (final Integer leadPartition : leadPartitions) {
      removeForPartitionId(leadPartition);
    }
    leadPartitions.clear();
  }

  @Override
  public ActorFuture<Void> onBecomingFollower(final int partitionId, final long term) {
    return removeLeaderHandlersAsync(partitionId);
  }

  @Override
  public ActorFuture<Void> onBecomingLeader(
      final int partitionId, final long term, final LogStream logStream) {
    final CompletableActorFuture<Void> future = new CompletableActorFuture<>();
    actor.call(
        () -> {
          leadPartitions.add(partitionId);
          limiter.addPartition(partitionId);

          logStream
              .newLogStreamRecordWriter()
              .onComplete(
                  (recordWriter, error) -> {
                    if (error == null) {

                      final var requestLimiter = limiter.getLimiter(partitionId);
                      commandRequestHandler.addPartition(partitionId, recordWriter, requestLimiter);
                      queryRequestHandler.addPartition(partitionId, recordWriter, requestLimiter);
                      serverTransport.subscribe(partitionId, commandRequestHandler, "command");
                      serverTransport.subscribe(partitionId, queryRequestHandler, "query");
                      future.complete(null);
                    } else {
                      Loggers.SYSTEM_LOGGER.error(
                          "Error on retrieving write buffer from log stream {}",
                          partitionId,
                          error);
                      future.completeExceptionally(error);
                    }
                  });
        });
    return future;
  }

  @Override
  public ActorFuture<Void> onBecomingInactive(final int partitionId, final long term) {
    return removeLeaderHandlersAsync(partitionId);
  }

  private ActorFuture<Void> removeLeaderHandlersAsync(final int partitionId) {
    return actor.call(
        () -> {
          commandRequestHandler.removePartition(partitionId);
          queryRequestHandler.removePartition(partitionId);
          cleanLeadingPartition(partitionId);
        });
  }

  private void cleanLeadingPartition(final int partitionId) {
    leadPartitions.remove(partitionId);
    removeForPartitionId(partitionId);
  }

  private void removeForPartitionId(final int partitionId) {
    limiter.removePartition(partitionId);
    serverTransport.unsubscribe(partitionId, "command");
    serverTransport.unsubscribe(partitionId, "query");
  }

  public CommandResponseWriter newCommandResponseWriter() {
    return new CommandResponseWriterImpl(serverTransport);
  }

  public Consumer<TypedRecord<?>> getOnProcessedListener(final int partitionId) {
    final RequestLimiter<Intent> partitionLimiter = limiter.getLimiter(partitionId);
    return typedRecord -> {
      if (typedRecord.getRecordType() == RecordType.COMMAND && typedRecord.hasRequestMetadata()) {
        partitionLimiter.onResponse(typedRecord.getRequestStreamId(), typedRecord.getRequestId());
      }
    };
  }

  @Override
  public void onDiskSpaceNotAvailable() {
    actor.run(commandRequestHandler::onDiskSpaceNotAvailable);
    actor.run(queryRequestHandler::onDiskSpaceNotAvailable);
  }

  @Override
  public void onDiskSpaceAvailable() {
    actor.run(commandRequestHandler::onDiskSpaceAvailable);
    actor.run(queryRequestHandler::onDiskSpaceAvailable);
  }
}
