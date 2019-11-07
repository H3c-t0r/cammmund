/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.clustering.base.partitions;

import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.zeebe.broker.Loggers;
import io.zeebe.broker.engine.EngineServiceNames;
import io.zeebe.broker.engine.impl.StateReplication;
import io.zeebe.broker.exporter.ExporterServiceNames;
import io.zeebe.broker.logstreams.delete.FollowerLogStreamDeletionService;
import io.zeebe.broker.logstreams.delete.LeaderLogStreamDeletionService;
import io.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.db.ZeebeDb;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.StateStorageFactory;
import io.zeebe.logstreams.impl.delete.DeletionService;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.state.NoneSnapshotReplication;
import io.zeebe.logstreams.state.SnapshotReplication;
import io.zeebe.logstreams.state.StateSnapshotController;
import io.zeebe.logstreams.state.StateStorage;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceStartContext;
import io.zeebe.servicecontainer.ServiceStopContext;
import org.slf4j.Logger;

/** Service representing a partition. */
public class Partition implements Service<Partition> {
  public static final String GROUP_NAME = "raft-atomix";
  public static final Logger LOG = Loggers.CLUSTERING_LOGGER;
  private static final String PARTITION_NAME_FORMAT = "raft-atomix-partition-%d";
  private final Injector<LogStream> logStreamInjector = new Injector<>();
  private final ClusterEventService clusterEventService;
  private final RaftState state;
  private final BrokerCfg brokerCfg;
  private final RaftPartition partition;
  private StateSnapshotController snapshotController;
  private SnapshotReplication stateReplication;
  private LogStream logStream;
  private ZeebeDb zeebeDb;

  public Partition(
      final BrokerCfg brokerCfg,
      final RaftPartition partition,
      final ClusterEventService clusterEventService,
      final RaftState state) {
    this.brokerCfg = brokerCfg;
    this.clusterEventService = clusterEventService;
    this.state = state;
    this.partition = partition;
  }

  public static String getPartitionName(final int partitionId) {
    return String.format(PARTITION_NAME_FORMAT, partitionId);
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    logStream = logStreamInjector.getValue();
    snapshotController = createSnapshotController();

    final DeletionService deletionService;
    if (state == RaftState.FOLLOWER) {
      final StatePositionSupplier positionSupplier =
          new StatePositionSupplier(
              snapshotController,
              getPartitionId(),
              String.valueOf(brokerCfg.getCluster().getNodeId()),
              LOG);
      deletionService = new FollowerLogStreamDeletionService(logStream, positionSupplier);

      snapshotController.setDeletionService(deletionService);
      snapshotController.consumeReplicatedSnapshots();
    } else {
      final LeaderLogStreamDeletionService leaderDeletionService =
          new LeaderLogStreamDeletionService(logStream);
      startContext
          .createService(
              EngineServiceNames.leaderLogStreamDeletionService(getPartitionId()),
              leaderDeletionService)
          .dependency(
              ExporterServiceNames.exporterDirectorServiceName(getPartitionId()),
              leaderDeletionService.getExporterDirectorInjector())
          .install();
      deletionService = leaderDeletionService;
      snapshotController.setDeletionService(deletionService);

      try {
        snapshotController.recover();
        zeebeDb = snapshotController.openDb();
      } catch (final Exception e) {
        throw new IllegalStateException(
            String.format(
                "Unexpected error occurred while recovering snapshot controller during leader partition install for partition %d",
                partition.id().id()),
            e);
      }
    }
  }

  @Override
  public void stop(final ServiceStopContext stopContext) {
    stateReplication.close();

    try {
      snapshotController.close();
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partition.id().id(),
          e);
    }
  }

  @Override
  public Partition get() {
    return this;
  }

  private StateSnapshotController createSnapshotController() {

    final StateStorageFactory storageFactory =
        new StateStorageFactory(partition.dataDirectory());
    final StateStorage stateStorage = storageFactory.create();

    stateReplication =
        shouldReplicateSnapshots()
            ? new StateReplication(clusterEventService, getPartitionId())
            : new NoneSnapshotReplication();

    return new StateSnapshotController(
        DefaultZeebeDbFactory.DEFAULT_DB_FACTORY,
        stateStorage,
        stateReplication,
        brokerCfg.getData().getMaxSnapshots());
  }

  private boolean shouldReplicateSnapshots() {
    return brokerCfg.getCluster().getReplicationFactor() > 1;
  }

  public int getPartitionId() {
    return partition.id().id();
  }

  public StateSnapshotController getSnapshotController() {
    return snapshotController;
  }

  public RaftState getState() {
    return state;
  }

  public LogStream getLogStream() {
    return logStream;
  }

  public Injector<LogStream> getLogStreamInjector() {
    return logStreamInjector;
  }

  public ZeebeDb getZeebeDb() {
    return zeebeDb;
  }
}
