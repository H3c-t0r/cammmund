/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db;

import java.nio.file.Path;
import java.util.Map;

public interface ChecksumProvider {

  /**
   * @param snapshotPath path of snapshot to get live file checksums
   * @return Map containing fileName - checksums pairs, where the checksums are unsigned integers in
   *     byte array format.
   */
  public Map<String, byte[]> getSnapshotChecksums(final Path snapshotPath);
}