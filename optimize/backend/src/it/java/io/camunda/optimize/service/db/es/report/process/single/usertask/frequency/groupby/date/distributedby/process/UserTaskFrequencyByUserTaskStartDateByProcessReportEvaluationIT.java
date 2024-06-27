/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.process.single.usertask.frequency.groupby.date.distributedby.process;

import io.camunda.optimize.dto.optimize.query.report.single.process.group.ProcessGroupByType;
import io.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import io.camunda.optimize.service.util.ProcessReportDataType;
import java.time.OffsetDateTime;

public class UserTaskFrequencyByUserTaskStartDateByProcessReportEvaluationIT
    extends UserTaskFrequencyByUserTaskDateByProcessReportEvaluationIT {

  @Override
  protected ProcessReportDataType getReportDataType() {
    return ProcessReportDataType.USER_TASK_FREQ_GROUP_BY_USER_TASK_START_DATE_BY_PROCESS;
  }

  @Override
  protected ProcessGroupByType getGroupByType() {
    return ProcessGroupByType.START_DATE;
  }

  @Override
  protected void changeUserTaskInstanceDate(
      final ProcessInstanceEngineDto processInstanceDto,
      final String flowNodeId,
      final OffsetDateTime date) {
    engineDatabaseExtension.changeFlowNodeStartDate(processInstanceDto.getId(), flowNodeId, date);
  }
}