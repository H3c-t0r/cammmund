/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.identity;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import org.agrona.DirectBuffer;

public final class UserRecord extends UnifiedRecordValue implements UserRecordValue {

  private final StringProperty usernameProp = new StringProperty("username");
  private final StringProperty nameProp = new StringProperty("name");
  private final StringProperty emailProp = new StringProperty("email");

  public UserRecord() {
    super(3);
    declareProperty(usernameProp).declareProperty(nameProp).declareProperty(emailProp);
  }

  public void wrap(final UserRecord record) {
    usernameProp.setValue(record.getUsernameBuffer());
    nameProp.setValue(record.getNameBuffer());
    emailProp.setValue(record.getEmailBuffer());
  }

  @Override
  public String getUsername() {
    return bufferAsString(usernameProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getUsernameBuffer() {
    return usernameProp.getValue();
  }

  public UserRecord setUsername(final String username) {
    usernameProp.setValue(username);
    return this;
  }

  public UserRecord setUsername(final DirectBuffer username) {
    usernameProp.setValue(username);
    return this;
  }

  @Override
  public String getName() {
    return bufferAsString(nameProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getNameBuffer() {
    return nameProp.getValue();
  }

  public UserRecord setName(final String name) {
    nameProp.setValue(name);
    return this;
  }

  public UserRecord setName(final DirectBuffer name) {
    nameProp.setValue(name);
    return this;
  }

  @Override
  public String getEmail() {
    return bufferAsString(emailProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getEmailBuffer() {
    return emailProp.getValue();
  }

  public UserRecord setEmail(final String email) {
    emailProp.setValue(email);
    return this;
  }

  public UserRecord setEmail(final DirectBuffer email) {
    emailProp.setValue(email);
    return this;
  }
}
