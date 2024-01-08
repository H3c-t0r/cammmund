/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

import java.util.Objects;

public class IncludeVariable {
  private String name;

  private boolean alwaysReturnFullValue = false;

  public String getName() {
    return name;
  }

  public IncludeVariable setName(String name) {
    this.name = name;
    return this;
  }

  public boolean isAlwaysReturnFullValue() {
    return alwaysReturnFullValue;
  }

  public IncludeVariable setAlwaysReturnFullValue(boolean alwaysReturnFullValue) {
    this.alwaysReturnFullValue = alwaysReturnFullValue;
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
    final IncludeVariable that = (IncludeVariable) o;
    return alwaysReturnFullValue == that.alwaysReturnFullValue && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(alwaysReturnFullValue, name);
  }
}
