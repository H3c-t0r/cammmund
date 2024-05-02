/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * A health report of a {@link #getComponentName() component}. If the status is not healthy, the
 * report also contains a {@link #getIssue() issue}.
 */
public final class HealthReport {
  private final HealthMonitorable component;
  private final String componentName;
  private final HealthStatus status;
  private final HealthIssue issue;

  private HealthReport(
      final HealthMonitorable component, final HealthStatus status, final HealthIssue issue) {
    this(component, component.getName(), status, issue);
  }

  private HealthReport(
      final HealthMonitorable component,
      final String componentName,
      final HealthStatus status,
      final HealthIssue issue) {
    this.component = component;
    this.componentName = componentName;
    this.status = status;
    this.issue = issue;
  }

  public static HealthReport unknown(final String componentName) {
    return new HealthReport(null, componentName, HealthStatus.UNHEALTHY, null);
  }

  public static HealthReport healthy(final HealthMonitorable component) {
    return new HealthReport(component, HealthStatus.HEALTHY, null);
  }

  public static HealthReportBuilder unhealthy(final HealthMonitorable component) {
    return new HealthReportBuilder(component, HealthStatus.UNHEALTHY);
  }

  public static HealthReportBuilder dead(final HealthMonitorable component) {
    return new HealthReportBuilder(component, HealthStatus.DEAD);
  }

  public boolean isHealthy() {
    return status == HealthStatus.HEALTHY;
  }

  public boolean isNotHealthy() {
    return status != HealthStatus.HEALTHY;
  }

  public boolean isUnhealthy() {
    return status == HealthStatus.UNHEALTHY;
  }

  public boolean isDead() {
    return status == HealthStatus.DEAD;
  }

  public String getComponentName() {
    return componentName;
  }

  public HealthStatus getStatus() {
    return status;
  }

  public HealthIssue getIssue() {
    return issue;
  }

  @Override
  public int hashCode() {
    int result = component != null ? component.hashCode() : 0;
    result = 31 * result + (componentName != null ? componentName.hashCode() : 0);
    result = 31 * result + (status != null ? status.hashCode() : 0);
    result = 31 * result + (issue != null ? issue.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final HealthReport that = (HealthReport) o;

    if (!Objects.equals(component, that.component)) {
      return false;
    }
    if (!Objects.equals(componentName, that.componentName)) {
      return false;
    }
    if (status != that.status) {
      return false;
    }
    return Objects.equals(issue, that.issue);
  }

  @Override
  public String toString() {
    final var name = componentName == null ? component.getName() : componentName;
    final var joiner = new StringJoiner(", ", name + "{", "}").add("status=" + status);
    if (issue != null) {
      joiner.add("issue=" + issue);
    }
    return joiner.toString();
  }

  public static final class HealthReportBuilder {
    private final HealthMonitorable component;
    private final HealthStatus status;

    private HealthReportBuilder(final HealthMonitorable component, final HealthStatus status) {
      this.component = component;
      this.status = status;
    }

    public HealthReport withIssue(final HealthIssue issue) {
      return new HealthReport(component, status, issue);
    }

    public HealthReport withMessage(final String message) {
      return new HealthReport(component, status, HealthIssue.of(message));
    }

    public HealthReport withIssue(final Throwable e) {
      return new HealthReport(component, status, HealthIssue.of(e));
    }

    public HealthReport withIssue(final HealthReport cause) {
      return new HealthReport(component, status, HealthIssue.of(cause));
    }
  }
}
