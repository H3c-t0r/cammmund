/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.FormMetadataValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessMetadataValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.Rule;
import org.junit.Test;

// TODO name
public class MultiResourceDeploymentTest {

  private static final String TEST_FORM_1_V1 = "/form/test-form-1.form";
  private static final String TEST_FORM_1_V2 = "/form/test-form-1_v2.form";
  private static final String DMN_DECISION_TABLE_V1 = "/dmn/decision-table.dmn";
  private static final String DMN_DECISION_TABLE_V2 = "/dmn/decision-table_v2.dmn";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test // TODO name / BPMN has changed
  public void shouldCreateNewVersionsOfAllResourcesIfAtLeastOneResourceHasChanged() {
    // given
    final var process1 =
        Bpmn.createExecutableProcess("process1").startEvent("v1").endEvent().done();
    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(process1)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();
    final var processV1 = firstDeployment.getProcessesMetadata().getFirst();
    final var drgV1 = firstDeployment.getDecisionRequirementsMetadata().getFirst();
    final var decisionV1 = firstDeployment.getDecisionsMetadata().getFirst();
    final var formV1 = firstDeployment.getFormMetadata().getFirst();

    // when
    final var process2 =
        Bpmn.createExecutableProcess("process1").startEvent("v2").endEvent().done();
    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(process2)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();

    // then
    final var processesMetadata = secondDeployment.getProcessesMetadata();
    assertThat(processesMetadata).hasSize(1);
    final var processV2 = processesMetadata.getFirst();
    assertOnProcessMetadata(processV2, processV1);

    final var decisionRequirementsMetadata = secondDeployment.getDecisionRequirementsMetadata();
    assertThat(decisionRequirementsMetadata).hasSize(1);
    final var drgV2 = decisionRequirementsMetadata.getFirst();
    assertOnDecisionRequirementsMetadata(drgV2, drgV1);

    final var decisionsMetadata = secondDeployment.getDecisionsMetadata();
    assertThat(decisionsMetadata).hasSize(1);
    final var decisionV2 = decisionsMetadata.getFirst();
    assertOnDecisionMetadata(decisionV2, drgV2, decisionV1);

    final var formMetadata = secondDeployment.getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    final var formV2 = formMetadata.getFirst();
    assertOnFormMetadata(formV2, formV1);

    assertOnProcessRecords(processV2);
    assertOnDecisionRequirementsRecords(drgV2);
    assertOnDecisionRecords(decisionV2, drgV2);
    assertOnFormRecords(formV2);
  }

  @Test // TODO name / DMN has changed
  public void shouldCreateNewVersionsOfAllResourcesIfAtLeastOneResourceHasChanged_dmn() {
    // given
    final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();
    final var processV1 = firstDeployment.getProcessesMetadata().getFirst();
    final var drgV1 = firstDeployment.getDecisionRequirementsMetadata().getFirst();
    final var decisionV1 = firstDeployment.getDecisionsMetadata().getFirst();
    final var formV1 = firstDeployment.getFormMetadata().getFirst();

    // when
    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V2)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();

    // then
    final var processesMetadata = secondDeployment.getProcessesMetadata();
    assertThat(processesMetadata).hasSize(1);
    final var processV2 = processesMetadata.getFirst();
    assertOnProcessMetadata(processV2, processV1);

    final var decisionRequirementsMetadata = secondDeployment.getDecisionRequirementsMetadata();
    assertThat(decisionRequirementsMetadata).hasSize(1);
    final var drgV2 = decisionRequirementsMetadata.getFirst();
    assertOnDecisionRequirementsMetadata(drgV2, drgV1);

    final var decisionsMetadata = secondDeployment.getDecisionsMetadata();
    assertThat(decisionsMetadata).hasSize(1);
    final var decisionV2 = decisionsMetadata.getFirst();
    assertOnDecisionMetadata(decisionV2, drgV2, decisionV1);

    final var formMetadata = secondDeployment.getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    final var formV2 = formMetadata.getFirst();
    assertOnFormMetadata(formV2, formV1);

    assertOnProcessRecords(processV2);
    assertOnDecisionRequirementsRecords(drgV2);
    assertOnDecisionRecords(decisionV2, drgV2);
    assertOnFormRecords(formV2);
  }

  @Test // TODO name / FORM has changed
  public void shouldCreateNewVersionsOfAllResourcesIfAtLeastOneResourceHasChanged_form() {
    // given
    final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();
    final var processV1 = firstDeployment.getProcessesMetadata().getFirst();
    final var drgV1 = firstDeployment.getDecisionRequirementsMetadata().getFirst();
    final var decisionV1 = firstDeployment.getDecisionsMetadata().getFirst();
    final var formV1 = firstDeployment.getFormMetadata().getFirst();

    // when
    final var secondDeployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V2)
            .deploy()
            .getValue();

    // then
    final var processesMetadata = secondDeployment.getProcessesMetadata();
    assertThat(processesMetadata).hasSize(1);
    final var processV2 = processesMetadata.getFirst();
    assertOnProcessMetadata(processV2, processV1);

    final var decisionRequirementsMetadata = secondDeployment.getDecisionRequirementsMetadata();
    assertThat(decisionRequirementsMetadata).hasSize(1);
    final var drgV2 = decisionRequirementsMetadata.getFirst();
    assertOnDecisionRequirementsMetadata(drgV2, drgV1);

    final var decisionsMetadata = secondDeployment.getDecisionsMetadata();
    assertThat(decisionsMetadata).hasSize(1);
    final var decisionV2 = decisionsMetadata.getFirst();
    assertOnDecisionMetadata(decisionV2, drgV2, decisionV1);

    final var formMetadata = secondDeployment.getFormMetadata();
    assertThat(formMetadata).hasSize(1);
    final var formV2 = formMetadata.getFirst();
    assertOnFormMetadata(formV2, formV1);

    assertOnProcessRecords(processV2);
    assertOnDecisionRequirementsRecords(drgV2);
    assertOnDecisionRecords(decisionV2, drgV2);
    assertOnFormRecords(formV2);
  }

  @Test // TODO name
  public void shouldNotCreateNewVersionsIfNoResourceHasChanged() {
    // given
    final var process = Bpmn.createExecutableProcess("process").startEvent().endEvent().done();
    final var firstDeployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();
    final var processV1 = firstDeployment.getProcessesMetadata().getFirst();
    final var drgV1 = firstDeployment.getDecisionRequirementsMetadata().getFirst();
    final var decisionV1 = firstDeployment.getDecisionsMetadata().getFirst();
    final var formV1 = firstDeployment.getFormMetadata().getFirst();

    // when
    final var secondDeployment =
        engine
            .deployment()
            // deploy the exact same resources again
            .withXmlResource(process)
            .withXmlClasspathResource(DMN_DECISION_TABLE_V1)
            .withJsonClasspathResource(TEST_FORM_1_V1)
            .deploy()
            .getValue();

    // then
    assertThat(secondDeployment.getProcessesMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .hasVersion(1)
                    .isDuplicate()
                    .extracting(
                        ProcessMetadataValue::getProcessDefinitionKey,
                        InstanceOfAssertFactories.LONG)
                    .isEqualTo(processV1.getProcessDefinitionKey()));
    assertThat(secondDeployment.getDecisionRequirementsMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .hasDecisionRequirementsVersion(1)
                    .isDuplicate()
                    .extracting(
                        DecisionRequirementsMetadataValue::getDecisionRequirementsKey,
                        InstanceOfAssertFactories.LONG)
                    .isEqualTo(drgV1.getDecisionRequirementsKey()));
    assertThat(secondDeployment.getDecisionsMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .hasVersion(1)
                    .isDuplicate()
                    .extracting(
                        DecisionRecordValue::getDecisionKey,
                        DecisionRecordValue::getDecisionRequirementsKey)
                    .containsExactly(
                        decisionV1.getDecisionKey(), drgV1.getDecisionRequirementsKey()));
    assertThat(secondDeployment.getFormMetadata())
        .singleElement()
        .satisfies(
            metadata ->
                Assertions.assertThat(metadata)
                    .hasVersion(1)
                    .isDuplicate()
                    .extracting(FormMetadataValue::getFormKey)
                    .isEqualTo(formV1.getFormKey()));

    // TODO why does it take so long?
    assertThat(RecordingExporter.processRecords().withIntent(ProcessIntent.CREATED).limit(2))
        .hasSize(1);
    assertThat(
            RecordingExporter.decisionRequirementsRecords()
                .withIntent(DecisionRequirementsIntent.CREATED)
                .limit(2))
        .hasSize(1);
    assertThat(RecordingExporter.decisionRecords().withIntent(DecisionIntent.CREATED).limit(2))
        .hasSize(1);
    assertThat(RecordingExporter.formRecords().withIntent(FormIntent.CREATED).limit(2)).hasSize(1);
  }

  private static void assertOnProcessMetadata(
      final ProcessMetadataValue processV2, final ProcessMetadataValue processV1) {
    Assertions.assertThat(processV2)
        .hasVersion(2)
        .isNotDuplicate()
        .extracting(ProcessMetadataValue::getProcessDefinitionKey, InstanceOfAssertFactories.LONG)
        .isGreaterThan(processV1.getProcessDefinitionKey());
  }

  private static void assertOnDecisionRequirementsMetadata(
      final DecisionRequirementsMetadataValue drgV2,
      final DecisionRequirementsMetadataValue drgV1) {
    Assertions.assertThat(drgV2)
        .hasDecisionRequirementsVersion(2)
        .isNotDuplicate()
        .extracting(
            DecisionRequirementsMetadataValue::getDecisionRequirementsKey,
            InstanceOfAssertFactories.LONG)
        .isGreaterThan(drgV1.getDecisionRequirementsKey());
  }

  private static void assertOnDecisionMetadata(
      final DecisionRecordValue decisionV2,
      final DecisionRequirementsMetadataValue drgV2,
      final DecisionRecordValue decisionV1) {
    Assertions.assertThat(decisionV2)
        .hasVersion(2)
        .hasDecisionRequirementsKey(drgV2.getDecisionRequirementsKey())
        .isNotDuplicate()
        .extracting(DecisionRecordValue::getDecisionKey, InstanceOfAssertFactories.LONG)
        .isGreaterThan(decisionV1.getDecisionKey());
  }

  private static void assertOnFormMetadata(
      final FormMetadataValue formV2, final FormMetadataValue formV1) {
    Assertions.assertThat(formV2)
        .hasVersion(2)
        .isNotDuplicate()
        .extracting(FormMetadataValue::getFormKey, InstanceOfAssertFactories.LONG)
        .isGreaterThan(formV1.getFormKey());
  }

  private static void assertOnProcessRecords(final ProcessMetadataValue processV2) {
    assertThat(
            RecordingExporter.processRecords().withIntent(ProcessIntent.CREATED).limit(2).getLast())
        .satisfies(
            record -> {
              assertThat(record.getKey()).isEqualTo(processV2.getProcessDefinitionKey());
              assertThat(record.getValue().getProcessDefinitionKey())
                  .isEqualTo(processV2.getProcessDefinitionKey());
              assertThat(record.getValue().getVersion()).isEqualTo(2);
            });
  }

  private static void assertOnDecisionRequirementsRecords(
      final DecisionRequirementsMetadataValue drgV2) {
    assertThat(
            RecordingExporter.decisionRequirementsRecords()
                .withIntent(DecisionRequirementsIntent.CREATED)
                .limit(2)
                .getLast())
        .satisfies(
            record -> {
              assertThat(record.getKey()).isEqualTo(drgV2.getDecisionRequirementsKey());
              assertThat(record.getValue().getDecisionRequirementsKey())
                  .isEqualTo(drgV2.getDecisionRequirementsKey());
              assertThat(record.getValue().getDecisionRequirementsVersion()).isEqualTo(2);
            });
  }

  private static void assertOnDecisionRecords(
      final DecisionRecordValue decisionV2, final DecisionRequirementsMetadataValue drgV2) {
    assertThat(
            RecordingExporter.decisionRecords()
                .withIntent(DecisionIntent.CREATED)
                .limit(2)
                .getLast())
        .satisfies(
            record -> {
              assertThat(record.getKey()).isEqualTo(decisionV2.getDecisionKey());
              assertThat(record.getValue().getDecisionKey()).isEqualTo(decisionV2.getDecisionKey());
              assertThat(record.getValue().getDecisionRequirementsKey())
                  .isEqualTo(drgV2.getDecisionRequirementsKey());
              assertThat(record.getValue().getVersion()).isEqualTo(2);
            });
  }

  private static void assertOnFormRecords(final FormMetadataValue formV2) {
    assertThat(RecordingExporter.formRecords().withIntent(FormIntent.CREATED).limit(2).getLast())
        .satisfies(
            record -> {
              assertThat(record.getKey()).isEqualTo(formV2.getFormKey());
              assertThat(record.getValue().getFormKey()).isEqualTo(formV2.getFormKey());
              assertThat(record.getValue().getVersion()).isEqualTo(2);
            });
  }
}
