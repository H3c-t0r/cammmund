package io.zeebe.broker.clustering.atomix.storage.snapshot;

import io.zeebe.logstreams.state.Snapshot;
import java.nio.file.Path;
import java.util.Objects;

final class SnapshotImpl implements Snapshot {
  private final long position;
  private final Path path;

  SnapshotImpl(final long position, final Path path) {
    this.position = position;
    this.path = path;
  }

  @Override
  public long getPosition() {
    return position;
  }

  @Override
  public Path getPath() {
    return path;
  }

  @Override
  public int hashCode() {
    return Objects.hash(getPosition(), getPath());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final SnapshotImpl snapshot = (SnapshotImpl) o;
    return getPosition() == snapshot.getPosition() && getPath().equals(snapshot.getPath());
  }
}
