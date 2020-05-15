/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.rest.eventprocess;

import org.assertj.core.groups.Tuple;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.event.EventProcessState;
import org.camunda.optimize.dto.optimize.query.event.EventSourceEntryDto;
import org.camunda.optimize.dto.optimize.rest.EventProcessMappingCreateRequestDto;
import org.camunda.optimize.dto.optimize.rest.event.EventProcessMappingResponseDto;
import org.camunda.optimize.dto.optimize.rest.event.EventSourceEntryResponseDto;
import org.camunda.optimize.service.EventProcessService;
import org.camunda.optimize.service.importing.eventprocess.AbstractEventProcessIT;

import javax.ws.rs.core.Response;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractEventProcessAutogenerationIT extends AbstractEventProcessIT {

  protected void assertUnmappedSavedEventBasedProcess(final EventProcessMappingResponseDto eventProcessMapping,
                                                      final List<EventSourceEntryDto> sources) {
    assertThat(eventProcessMapping.getName()).isEqualTo(EventProcessService.DEFAULT_AUTOGENERATED_PROCESS_NAME);
    assertThat(eventProcessMapping.getState()).isEqualTo(EventProcessState.UNMAPPED);
    assertThat(eventProcessMapping.getEventSources()).extracting(
      EventSourceEntryResponseDto::getEventScope,
      EventSourceEntryResponseDto::getType
    ).containsExactlyInAnyOrderElementsOf(
      sources.stream()
        .map(source -> Tuple.tuple(source.getEventScope(), source.getType()))
        .collect(Collectors.toList()));
  }

  protected EventProcessMappingResponseDto autogenerateProcessAndGetMappingResponse(final EventProcessMappingCreateRequestDto createRequestDto) {
    String processId = eventProcessClient.createCreateEventProcessMappingRequest(createRequestDto)
      .execute(IdDto.class, Response.Status.OK.getStatusCode()).getId();
    return eventProcessClient.getEventProcessMapping(processId);
  }

  protected EventProcessMappingCreateRequestDto buildAutogenerateCreateRequestDto(final List<EventSourceEntryDto> sources) {
    return EventProcessMappingCreateRequestDto.eventProcessMappingCreateBuilder()
      .eventSources(sources)
      .autogenerate(true)
      .build();
  }

  protected void processEventCountAndTraces() {
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    embeddedOptimizeExtension.processEvents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
  }

}
