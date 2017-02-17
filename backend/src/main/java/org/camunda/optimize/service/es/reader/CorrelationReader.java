package org.camunda.optimize.service.es.reader;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.SequenceFlow;
import org.camunda.optimize.dto.optimize.CorrelationOutcomeDto;
import org.camunda.optimize.dto.optimize.CorrelationQueryDto;
import org.camunda.optimize.dto.optimize.FilterDto;
import org.camunda.optimize.dto.optimize.GatewaySplitDto;
import org.camunda.optimize.service.es.mapping.DateFilterHelper;
import org.camunda.optimize.service.util.ConfigurationService;
import org.camunda.optimize.service.util.ValidationHelper;
import org.elasticsearch.action.search.SearchRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.metrics.scripted.InternalScriptedMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * @author Askar Akhmerov
 */
@Component
public class CorrelationReader {

  @Autowired
  private TransportClient esclient;
  @Autowired
  private ConfigurationService configurationService;

  @Autowired
  private ProcessDefinitionReader processDefinitionReader;

  @Autowired
  private DateFilterHelper dateFilterHelper;

  public GatewaySplitDto activityCorrelation(CorrelationQueryDto request) {
    ValidationHelper.validate(request);
    GatewaySplitDto result = new GatewaySplitDto();
    List<String> gatewayOutcomes = fetchGatewayOutcomes(request.getProcessDefinitionId(), request.getGateway());

    for (String activity : gatewayOutcomes) {
      CorrelationOutcomeDto correlation = activityCorrelation(
          request.getProcessDefinitionId(),
          activity,
          request.getEnd(),
          request.getFilter()
      );
      result.getFollowingNodes().put(correlation.getId(), correlation);
    }

    CorrelationOutcomeDto end = activityCorrelation(
        request.getProcessDefinitionId(),
        request.getEnd(),
        request.getEnd(),
        request.getFilter()
    );
    result.setEndEvent(end.getId());
    result.setTotal(end.getAll());

    return result;
  }

  public CorrelationOutcomeDto activityCorrelation(String processDefinitionId, String activityId, String endActivity, FilterDto filter) {
    ValidationHelper.ensureNotEmpty("processDefinitionId", processDefinitionId);
    ValidationHelper.ensureNotEmpty("activityId", activityId);
    ValidationHelper.ensureNotEmpty("endActivityId", endActivity);

    CorrelationOutcomeDto result = new CorrelationOutcomeDto();

    List<String> correlationNodes = new ArrayList<>();
    correlationNodes.add(activityId);
    correlationNodes.add(endActivity);

    BoolQueryBuilder query;
    SearchRequestBuilder srb = esclient
        .prepareSearch(configurationService.getOptimizeIndex())
        .setTypes(configurationService.getEventType());

    query = QueryBuilders.boolQuery()
        .must(QueryBuilders.matchQuery("processDefinitionId", processDefinitionId));

    if (filter != null) {
      query = dateFilterHelper.addFilters(query, filter);
    }

    srb.setQuery(query);


    Map<String, Object> parameters = new HashMap<>();
    parameters.put("_targetActivities", correlationNodes);
    parameters.put("_startActivity", activityId);
    parameters.put("_agg", new HashMap<>());


    SearchResponse sr = srb
        .addAggregation(AggregationBuilders
            .scriptedMetric("processesWithActivities")
            .initScript(getInitScript())
            .mapScript(getMapScript())
            .reduceScript(getReduceScript())
            .params(parameters)
        )
        .get();

    InternalScriptedMetric processesWithActivities = sr.getAggregations().get("processesWithActivities");
    Map aggregation = (Map) processesWithActivities.aggregation();
    String id = aggregation.get("id") != null ? aggregation.get("id").toString() : activityId;
    result.setId(id);
    result.setAll(Long.valueOf((Integer) aggregation.get("all")));
    result.setReached(Long.valueOf((Integer) aggregation.get("reached")));

    return result;
  }

  private List<String> fetchGatewayOutcomes(String processDefinitionId, String gatewayActivityId) {
    List<String> result = new ArrayList<>();
    String xml = processDefinitionReader.getProcessDefinitionXml(processDefinitionId);
    BpmnModelInstance bpmnModelInstance = Bpmn.readModelFromStream(new ByteArrayInputStream(xml.getBytes()));
    FlowNode flowNode = bpmnModelInstance.getModelElementById(gatewayActivityId);
    for (SequenceFlow sequence : flowNode.getOutgoing()) {
      result.add(sequence.getTarget().getId());
    }
    return result;
  }

  private Script getReduceScript() {
    return new Script(
        ScriptType.FILE,
        "groovy",
        configurationService.getCorrelationReduceScriptPath(),
        new HashMap<>()
    );
  }

  private Script getMapScript() {
    return new Script(
        ScriptType.FILE,
        "groovy",
        configurationService.getCorrelationMapScriptPath(),
        new HashMap<>()
    );
  }

  private Script getInitScript() {
    return new Script(
        ScriptType.FILE,
        "groovy",
        configurationService.getCorrelationInitScriptPath(),
        new HashMap<>()
    );
  }

  private String getContent(String script) {
    InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(script);
    Scanner s = new Scanner(inputStream).useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }

  public CorrelationOutcomeDto activityCorrelation(String processDefinitionId, String gatewayActivity, String endActivity) {
    return this.activityCorrelation(processDefinitionId, gatewayActivity, endActivity, null);
  }
}
