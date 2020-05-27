/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select} from 'components';
import {t} from 'translation';

export default function DistributedBy({
  report: {
    data: {configuration, view, groupBy, visualization},
  },
  onChange,
}) {
  if (view && view.entity === 'userTask' && groupBy) {
    return (
      <fieldset className="DistributedBy">
        <legend>{t('report.config.userTaskDistributedBy')}</legend>
        <Select
          value={configuration.distributedBy}
          onChange={(value) => {
            if (value !== 'none' && (visualization === 'pie' || visualization === 'line')) {
              onChange(
                {visualization: {$set: 'bar'}, configuration: {distributedBy: {$set: value}}},
                true
              );
            } else {
              onChange({configuration: {distributedBy: {$set: value}}}, true);
            }
          }}
        >
          <Select.Option value="none">{t('common.nothing')}</Select.Option>
          {getOptionsFor(groupBy.type)}
        </Select>
      </fieldset>
    );
  }
  return null;
}

function getOptionsFor(type) {
  const options = [];

  if (['userTasks', 'startDate', 'endDate'].includes(type)) {
    options.push(
      <Select.Option key="assignee" value="assignee">
        {t('report.groupBy.userAssignee')}
      </Select.Option>,
      <Select.Option key="candidateGroup" value="candidateGroup">
        {t('report.groupBy.userGroup')}
      </Select.Option>
    );
  }

  if (type !== 'userTasks') {
    options.push(
      <Select.Option key="userTask" value="userTask">
        {t('report.view.userTask')}
      </Select.Option>
    );
  }

  return options;
}
