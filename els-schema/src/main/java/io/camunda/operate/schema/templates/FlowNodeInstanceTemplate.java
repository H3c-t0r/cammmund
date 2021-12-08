/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant {

  public static final String INDEX_NAME = "flownode-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String TREE_PATH = "treePath";
  public static final String LEVEL = "level";
  public static final String INCIDENT = "incident";     //true/false

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "1.3.0";
  }
}
