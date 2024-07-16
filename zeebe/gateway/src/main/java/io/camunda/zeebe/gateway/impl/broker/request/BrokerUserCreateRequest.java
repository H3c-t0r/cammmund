package io.camunda.zeebe.gateway.impl.broker.request;

import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.identity.UserRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import org.agrona.DirectBuffer;

public class BrokerUserCreateRequest extends BrokerExecuteCommand<UserRecord> {

  private final UserRecord requestDto = new UserRecord();

  public BrokerUserCreateRequest() {
    super(ValueType.USER, UserIntent.CREATE);
  }

  public BrokerUserCreateRequest setUsername(final String username) {
    requestDto.setUsername(username);
    return this;
  }

  public BrokerUserCreateRequest setName(final String name) {
    requestDto.setName(name);
    return this;
  }

  public BrokerUserCreateRequest setEmail(final String email) {
    requestDto.setEmail(email);
    return this;
  }

  @Override
  public UserRecord getRequestWriter() {
    return requestDto;
  }

  @Override
  protected UserRecord toResponseDto(final DirectBuffer buffer) {
    final var response = new UserRecord();
    response.wrap(buffer);
    return response;
  }
}
