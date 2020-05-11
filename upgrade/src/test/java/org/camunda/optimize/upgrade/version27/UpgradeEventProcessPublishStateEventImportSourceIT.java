/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.upgrade.version27;

import org.camunda.optimize.dto.optimize.query.event.EventScopeType;
import org.camunda.optimize.dto.optimize.query.event.EventSourceType;
import org.camunda.optimize.dto.optimize.query.event.IndexableEventProcessPublishStateDto;
import org.camunda.optimize.upgrade.AbstractUpgradeIT;
import org.camunda.optimize.upgrade.main.impl.UpgradeFrom27To30;
import org.camunda.optimize.upgrade.plan.UpgradePlan;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class UpgradeEventProcessPublishStateEventImportSourceIT extends AbstractUpgradeIT {

  @BeforeEach
  @Override
  public void setUp() throws Exception {
    super.setUp();

    initSchema(ALL_INDEXES);
    setMetadataIndexVersion(FROM_VERSION);

    executeBulk("steps/event_process_publish_states/27-event-process-publish-state-bulk");
  }

  @Test
  public void addEventLabelFieldToEvents() throws IOException {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<IndexableEventProcessPublishStateDto> eventProcessPublishStates = getEventProcessPublishStates();
    assertThat(eventProcessPublishStates).hasSize(3);
    assertThat(eventProcessPublishStates)
      .extracting(IndexableEventProcessPublishStateDto::getMappings)
      .allSatisfy(mappings -> {
        assertThat(
          mappings.stream()
            .allMatch(mapping -> (mapping.getStart() == null || mapping.getStart().getEventLabel() == null)
              && (mapping.getEnd() == null || mapping.getEnd().getEventLabel() == null)));
      });
  }

  @Test
  public void addEventImportSourcesFieldToExistingEventPublishStates() throws IOException {
    // given
    final UpgradePlan upgradePlan = new UpgradeFrom27To30().buildUpgradePlan();

    // when
    upgradePlan.execute();

    // then
    List<IndexableEventProcessPublishStateDto> eventProcessPublishStates = getEventProcessPublishStates();
    assertThat(eventProcessPublishStates).hasSize(3);
    assertThat(eventProcessPublishStates)
      .extracting(IndexableEventProcessPublishStateDto::getEventImportSources)
      .allSatisfy(importSources -> assertThat(importSources)
        .hasSize(1)
        .allSatisfy(importSource -> {
          assertThat(importSource.getLastImportedEventTimestamp()).isNotNull();
          assertThat(importSource.getLastEventForSourceAtTimeOfPublishTimestamp()).isNotNull();
          assertThat(importSource.getFirstEventForSourceAtTimeOfPublishTimestamp()).isNotNull();
          assertThat(importSource.getEventSource())
            .satisfies(source -> {
              assertThat(source.getId()).isNotNull();
              assertThat(source.getType()).isEqualTo(EventSourceType.EXTERNAL);
              assertThat(source.getEventScope()).containsExactly(EventScopeType.ALL);
            });
        }));
  }

  private List<IndexableEventProcessPublishStateDto> getEventProcessPublishStates() throws IOException {
    final SearchResponse searchResponse = prefixAwareClient.search(
      new SearchRequest(EVENT_PROCESS_PUBLISH_STATE_INDEX_V1.getIndexName()).source(new SearchSourceBuilder().size(10000)),
      RequestOptions.DEFAULT
    );
    return Arrays
      .stream(searchResponse.getHits().getHits())
      .map(doc -> {
        try {
          return objectMapper.readValue(
            doc.getSourceAsString(), IndexableEventProcessPublishStateDto.class
          );
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      })
      .collect(Collectors.toList());
  }

}
