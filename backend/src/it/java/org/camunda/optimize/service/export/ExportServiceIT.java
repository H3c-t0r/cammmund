package org.camunda.optimize.service.export;

import org.apache.commons.io.IOUtils;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineDatabaseRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.camunda.optimize.test.util.ReportDataHelper;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.ws.rs.client.Entity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.service.es.report.command.util.ReportConstants.DATE_UNIT_DAY;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

/**
 * @author Askar Akhmerov
 */
@RunWith(Parameterized.class)
public class ExportServiceIT {

  @Parameterized.Parameters(name = "{2}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
      {
        ReportDataHelper.createReportDataViewRawAsTable(
          FAKE,
          FAKE
        ),
        "/csv/raw_data_grouped_by_none.csv",
        "Raw Data Grouped By None"
      },
      {
        ReportDataHelper.createReportRawDataGroupByFlowNodesAsTable(
          FAKE,
          FAKE
        ),
        "/csv/raw_data_grouped_by_none.csv",
        "Raw Data Grouped By Flow Nodes"
      },
      {
        ReportDataHelper.createReportRawDataGroupByStartDateAsTable(
          FAKE,
          FAKE
        ),
        "/csv/raw_data_grouped_by_none.csv",
        "Raw Data Grouped By PI Start Date"
      },
      {
        ReportDataHelper.createPICountFrequencyGroupByStartDate(
          FAKE,
          FAKE,
          DATE_UNIT_DAY
        ),
        "/csv/count_pi_frequency_group_by_start_date.csv",
        "Count PI Grouped By PI Start Date"
      },
      {
        ReportDataHelper.createCountFlowNodeFrequencyGroupByFlowNode(
          FAKE,
          FAKE
        ),
        "/csv/count_flownode_frequency_group_by_flownode.csv",
        "Count Flow Nodes Grouped By Flow Node"
      },
      {
        ReportDataHelper.createAvgPIDurationGroupByStartDateReport(
          FAKE,
          FAKE,
          DATE_UNIT_DAY
        ),
        "/csv/avg_pi_duration_group_by_start_date.csv",
        "Avg PI Duration Grouped By PI Start Date"
      },
      {
        ReportDataHelper.createAverageFlowNodeDurationGroupByFlowNodeHeatmapReport(
          FAKE,
          FAKE
        ),
        "/csv/avg_flownode_duration_group_by_flownode.csv",
        "Avg Flow Node Duration Grouped By Flow Node"
      }
    });
  }

  private ReportDataDto currentReport;
  private String expectedCSV;

  protected static final String BEARER = "Bearer ";
  protected static final String FAKE = "FAKE";
  protected static final String CSV_EXPORT = "export/csv";

  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();
  public EngineDatabaseRule engineDatabaseRule = new EngineDatabaseRule();

  @Rule
  public RuleChain chain = RuleChain
      .outerRule(elasticSearchRule)
      .around(engineRule)
      .around(embeddedOptimizeRule)
      .around(engineDatabaseRule);

  public ExportServiceIT(ReportDataDto currentReport, String expectedCSV, String testName) {
    this.currentReport = currentReport;
    this.expectedCSV = expectedCSV;
  }

  @Test
  public void reportCsvHasExpectedValue() throws Exception {
    //given
    String token = embeddedOptimizeRule.getAuthenticationToken();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();

    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    currentReport.setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
    currentReport.setProcessDefinitionVersion(processInstance.getProcessDefinitionVersion());
    String reportId = createAndStoreDefaultReportDefinition(currentReport);

    // when
    Response response =
        embeddedOptimizeRule.target(CSV_EXPORT + "/" + reportId + "/my_file.csv")
            .request()
            .header(HttpHeaders.AUTHORIZATION, BEARER + token)
            .get();

    // then
    assertThat(response.getStatus(), is(200));
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    IOUtils.copy(response.readEntity(InputStream.class), bos);
    byte[] result = bos.toByteArray();
    assertThat(result.length, is(not(0)));

    Path path = Paths.get(this.getClass().getResource(expectedCSV).getPath());
    byte[] expectedContent = Files.readAllBytes(path);
    String stringExpected = new String(expectedContent);
    stringExpected  = stringExpected.
      replace("${PI_ID}", processInstance.getId());
    stringExpected  = stringExpected.
      replace("${PD_ID}", processInstance.getDefinitionId());

    assertThat(new String(result), is(stringExpected));
  }

  private String createAndStoreDefaultReportDefinition(ReportDataDto reportData) {
    String id = createNewReportHelper();
    ReportDefinitionDto report = new ReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    updateReport(id, report);
    return id;
  }

  private void updateReport(String id, ReportDefinitionDto updatedReport) {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report/" + id)
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .put(Entity.json(updatedReport));
    assertThat(response.getStatus(), is(204));
  }


  protected String createNewReportHelper() {
    String token = embeddedOptimizeRule.getAuthenticationToken();
    Response response =
        embeddedOptimizeRule.target("report")
            .request()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .post(Entity.json(""));
    assertThat(response.getStatus(), is(200));

    return response.readEntity(IdDto.class).getId();
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcess() throws Exception {
    HashMap<String, Object> variables = new HashMap<>();
    variables.put("1", "test");
    ProcessInstanceEngineDto processInstanceEngineDto = deployAndStartSimpleProcessWithVariables(variables);

    OffsetDateTime shiftedStartDate = OffsetDateTime.parse("2018-02-26T14:20:50.189+01:00");
    engineDatabaseRule.changeProcessInstanceStartDate(processInstanceEngineDto.getId(), shiftedStartDate);
    engineDatabaseRule.changeProcessInstanceEndDate(processInstanceEngineDto.getId(), shiftedStartDate);
    return processInstanceEngineDto;
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
        .name("aProcessName")
        .startEvent("aStart")
        .endEvent("anEnd")
        .done();
    return engineRule.deployAndStartProcessWithVariables(processModel, variables);
  }
}