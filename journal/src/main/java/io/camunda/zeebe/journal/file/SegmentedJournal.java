/*
 * Copyright 2017-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.journal.file;

import static com.google.common.base.Preconditions.checkState;

import com.google.common.collect.Sets;
import io.camunda.zeebe.journal.Journal;
import io.camunda.zeebe.journal.JournalMetaStore;
import io.camunda.zeebe.journal.JournalReader;
import io.camunda.zeebe.journal.JournalRecord;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;

/** A file based journal. The journal is split into multiple segments files. */
public final class SegmentedJournal implements Journal {
  public static final long ASQN_IGNORE = -1;
  private final JournalMetrics journalMetrics;
  private final Collection<SegmentedJournalReader> readers = Sets.newConcurrentHashSet();
  private volatile boolean open = true;
  private final JournalIndex journalIndex;
  private final SegmentedJournalWriter writer;
  private final StampedLock rwlock = new StampedLock();
  private final SegmentsManager segments;

  private final JournalMetaStore journalMetaStore;

  SegmentedJournal(
      final JournalIndex journalIndex,
      final SegmentsManager segments,
      final JournalMetrics journalMetrics,
      final JournalMetaStore journalMetaStore) {
    this.journalMetrics = Objects.requireNonNull(journalMetrics, "must specify journal metrics");
    this.journalIndex = Objects.requireNonNull(journalIndex, "must specify a journal index");
    this.segments = Objects.requireNonNull(segments, "must specify a journal segments manager");
    this.journalMetaStore =
        Objects.requireNonNull(journalMetaStore, "must specify a journal meta store");

    this.segments.open();
    writer = new SegmentedJournalWriter(this);
  }

  /**
   * Returns a new SegmentedJournal builder.
   *
   * @return A new Segmented journal builder.
   */
  public static SegmentedJournalBuilder builder() {
    return new SegmentedJournalBuilder();
  }

  @Override
  public JournalRecord append(final BufferWriter recordDataWriter) {
    return append(ASQN_IGNORE, recordDataWriter);
  }

  @Override
  public JournalRecord append(final long asqn, final BufferWriter recordDataWriter) {
    return writer.append(asqn, recordDataWriter);
  }

  @Override
  public void append(final JournalRecord record) {
    writer.append(record);
  }

  @Override
  public void deleteAfter(final long indexExclusive) {
    journalMetrics.observeSegmentTruncation(
        () -> {
          final var stamp = rwlock.writeLock();
          try {
            writer.deleteAfter(indexExclusive);
            // Reset segment readers.
            resetAdvancedReaders(indexExclusive + 1);
          } finally {
            rwlock.unlockWrite(stamp);
          }
        });
  }

  @Override
  public void deleteUntil(final long index) {
    final var stamp = rwlock.writeLock();
    try {
      segments.deleteUntil(index);
    } finally {
      rwlock.unlockWrite(stamp);
    }
  }

  @Override
  public void reset(final long nextIndex) {
    final var stamp = rwlock.writeLock();
    try {
      journalIndex.clear();
      writer.reset(nextIndex);
      // no need to update the meta store's last flushed index as usage is that we always reset
      // with a greater index than what we previously had. it's fine if the stored last flushed
      // index is lower than the real flushed index. every thing will be treated as a partial write
      // until the next flush, which is fine
    } finally {
      rwlock.unlockWrite(stamp);
    }
  }

  @Override
  public long getLastIndex() {
    return writer.getLastIndex();
  }

  @Override
  public long getFirstIndex() {
    final var firstSegment = segments.getFirstSegment();
    return firstSegment != null ? firstSegment.index() : 0;
  }

  @Override
  public boolean isEmpty() {
    return writer.getNextIndex() - getFirstSegment().index() == 0;
  }

  @Override
  public void flush() {
    writer.flush();
    journalMetaStore.storeLastFlushedIndex(getLastIndex());
  }

  @Override
  public JournalReader openReader() {
    final var stamped = acquireReadlock();
    try {
      final var reader = new SegmentedJournalReader(this);
      readers.add(reader);
      return reader;
    } finally {
      releaseReadlock(stamped);
    }
  }

  @Override
  public boolean isOpen() {
    return open;
  }

  @Override
  public void close() {
    flush();
    segments.close();
    open = false;
  }

  /**
   * Asserts that the journal is open.
   *
   * @throws IllegalStateException if the journal is not open
   */
  private void assertOpen() {
    checkState(segments.getCurrentSegment() != null, "journal not open");
  }

  /**
   * Returns the first segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getFirstSegment() {
    assertOpen();
    return segments.getFirstSegment();
  }

  /**
   * Returns the last segment in the log.
   *
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getLastSegment() {
    assertOpen();
    return segments.getLastSegment();
  }

  Segment getNextSegment() {
    assertOpen();
    return segments.getNextSegment();
  }

  /**
   * Returns the segment following the segment with the given ID.
   *
   * @param index The segment index with which to look up the next segment.
   * @return The next segment for the given index.
   */
  Segment getNextSegment(final long index) {
    return segments.getNextSegment(index);
  }

  /**
   * Returns the segment for the given index.
   *
   * @param index The index for which to return the segment.
   * @throws IllegalStateException if the segment manager is not open
   */
  Segment getSegment(final long index) {
    assertOpen();
    return segments.getSegment(index);
  }

  public void closeReader(final SegmentedJournalReader segmentedJournalReader) {
    readers.remove(segmentedJournalReader);
  }

  /**
   * Resets and returns the first segment in the journal.
   *
   * @param index the starting index of the journal
   * @return the first segment
   */
  Segment resetSegments(final long index) {
    return segments.resetSegments(index);
  }

  /**
   * Removes a segment.
   *
   * @param segment The segment to remove.
   */
  synchronized void removeSegment(final Segment segment) {
    segments.removeSegment(segment);
  }
  /**
   * Resets journal readers to the given index, if they are at a larger index.
   *
   * @param index The index at which to reset readers.
   */
  void resetAdvancedReaders(final long index) {
    for (final SegmentedJournalReader reader : readers) {
      if (reader.getNextIndex() > index) {
        reader.unsafeSeek(index);
      }
    }
  }

  public JournalMetrics getJournalMetrics() {
    return journalMetrics;
  }

  public JournalIndex getJournalIndex() {
    return journalIndex;
  }

  long acquireReadlock() {
    return rwlock.readLock();
  }

  void releaseReadlock(final long stamp) {
    rwlock.unlockRead(stamp);
  }
}