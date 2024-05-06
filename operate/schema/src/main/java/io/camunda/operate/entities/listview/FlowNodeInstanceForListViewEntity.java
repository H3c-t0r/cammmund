/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities.listview;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.entities.FlowNodeType;
import io.camunda.operate.entities.OperateZeebeEntity;
import io.camunda.operate.schema.templates.ListViewTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FlowNodeInstanceForListViewEntity
    extends OperateZeebeEntity<FlowNodeInstanceForListViewEntity> {

  private Long processInstanceKey;
  private String activityId;
  private FlowNodeState activityState;
  private FlowNodeType activityType;
  @Deprecated @JsonIgnore private List<Long> incidentKeys = new ArrayList<>();
  private String errorMessage;
  private boolean incident;
  private boolean jobFailedWithRetriesLeft = false;

  private String tenantId;

  @Deprecated @JsonIgnore private boolean pendingIncident;

  private ListViewJoinRelation joinRelation =
      new ListViewJoinRelation(ListViewTemplate.ACTIVITIES_JOIN_RELATION);

  @JsonIgnore private Long startTime;
  @JsonIgnore private Long endTime;

  public Long getProcessInstanceKey() {
    return processInstanceKey;
  }

  public void setProcessInstanceKey(Long processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }

  public String getActivityId() {
    return activityId;
  }

  public void setActivityId(String activityId) {
    this.activityId = activityId;
  }

  public FlowNodeState getActivityState() {
    return activityState;
  }

  public void setActivityState(FlowNodeState activityState) {
    this.activityState = activityState;
  }

  public FlowNodeType getActivityType() {
    return activityType;
  }

  public void setActivityType(FlowNodeType activityType) {
    this.activityType = activityType;
  }

  public List<Long> getIncidentKeys() {
    return incidentKeys;
  }

  public FlowNodeInstanceForListViewEntity setIncidentKeys(List<Long> incidentKeys) {
    this.incidentKeys = incidentKeys;
    return this;
  }

  public FlowNodeInstanceForListViewEntity addIncidentKey(Long incidentKey) {
    this.incidentKeys.add(incidentKey);
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public boolean isIncident() {
    return incident;
  }

  public FlowNodeInstanceForListViewEntity setIncident(final boolean incident) {
    this.incident = incident;
    return this;
  }

  public String getTenantId() {
    return tenantId;
  }

  public FlowNodeInstanceForListViewEntity setTenantId(String tenantId) {
    this.tenantId = tenantId;
    return this;
  }

  public boolean isPendingIncident() {
    return pendingIncident;
  }

  public FlowNodeInstanceForListViewEntity setPendingIncident(boolean pendingIncident) {
    this.pendingIncident = pendingIncident;
    return this;
  }

  public ListViewJoinRelation getJoinRelation() {
    return joinRelation;
  }

  public void setJoinRelation(ListViewJoinRelation joinRelation) {
    this.joinRelation = joinRelation;
  }

  public Long getStartTime() {
    return startTime;
  }

  public void setStartTime(Long startTime) {
    this.startTime = startTime;
  }

  public Long getEndTime() {
    return endTime;
  }

  public void setEndTime(Long endTime) {
    this.endTime = endTime;
  }

  public boolean isJobFailedWithRetriesLeft() {
    return jobFailedWithRetriesLeft;
  }

  public FlowNodeInstanceForListViewEntity setJobFailedWithRetriesLeft(
      boolean jobFailedWithRetriesLeft) {
    this.jobFailedWithRetriesLeft = jobFailedWithRetriesLeft;
    return this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final FlowNodeInstanceForListViewEntity that = (FlowNodeInstanceForListViewEntity) o;
    return incident == that.incident
        && Objects.equals(processInstanceKey, that.processInstanceKey)
        && Objects.equals(activityId, that.activityId)
        && activityState == that.activityState
        && activityType == that.activityType
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(joinRelation, that.joinRelation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        processInstanceKey,
        activityId,
        activityState,
        activityType,
        errorMessage,
        incident,
        joinRelation);
  }
}