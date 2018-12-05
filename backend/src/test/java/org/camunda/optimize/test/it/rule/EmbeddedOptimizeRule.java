package org.camunda.optimize.test.it.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.camunda.optimize.OptimizeRequestExecutor;
import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.dto.optimize.query.security.CredentialsDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.rest.engine.EngineContextFactory;
import org.camunda.optimize.rest.util.AuthenticationUtil;
import org.camunda.optimize.service.alert.AlertService;
import org.camunda.optimize.service.cleanup.OptimizeCleanupService;
import org.camunda.optimize.service.engine.importing.EngineImportScheduler;
import org.camunda.optimize.service.engine.importing.EngineImportSchedulerFactory;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.TimestampBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.RunningActivityInstanceImportService;
import org.camunda.optimize.service.engine.importing.service.mediator.EngineImportMediator;
import org.camunda.optimize.service.engine.importing.service.mediator.StoreIndexesEngineImportMediator;
import org.camunda.optimize.service.es.ElasticSearchSchemaInitializer;
import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;
import org.camunda.optimize.service.es.schema.ElasticSearchSchemaManager;
import org.camunda.optimize.service.es.writer.RunningActivityInstanceWriter;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BeanHelper;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.test.util.SynchronizationElasticsearchImportJob;
import org.elasticsearch.client.Client;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Helper rule to start embedded jetty with Camunda Optimize on bord.
 */
public class EmbeddedOptimizeRule extends TestWatcher {

  private String context = null;
  private Logger logger = LoggerFactory.getLogger(EmbeddedOptimizeRule.class);
  private OptimizeRequestExecutor requestExecutor;

  /**
   * 1. Reset import start indexes
   *
   * 2. Schedule import of all entities, execute all available jobs sequentially
   * until nothing more exists in scheduler queue.
   *
   * NOTE: this will not store indexes in the ES.
   */
  public EmbeddedOptimizeRule() {}

  public EmbeddedOptimizeRule(String context) {
    this.context = context;
  }

  public void startContinuousImportScheduling() {
    getOptimize().startImportSchedulers();
  }

  public void scheduleAllJobsAndImportEngineEntities() {
    try {
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do
    }
    resetBackoffAndScheduleAllImportsAndWaitUntilFinished();
  }

  public void resetBackoffAndScheduleAllImportsAndWaitUntilFinished() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      if (scheduler.isEnabled()) {
        logger.debug("scheduling first import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
        // we need another round for the scroll based import index handler
        logger.debug("scheduling second import round");
        scheduleImportAndWaitUntilIsFinished(scheduler);
      }
    }

  }

  public void importRunningActivityInstance(List<HistoricActivityInstanceEngineDto> activities) {
    RunningActivityInstanceWriter writer = getApplicationContext().getBean(RunningActivityInstanceWriter.class);

    for (EngineContext configuredEngine : getConfiguredEngines()) {
      RunningActivityInstanceImportService service =
        new RunningActivityInstanceImportService(writer, getElasticsearchImportJobExecutor(), configuredEngine);
      service.executeImport(activities);
    }
    makeSureAllScheduledJobsAreFinished();
  }

  private void resetImportBackoff() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler
        .getImportMediators()
        .forEach(EngineImportMediator::resetBackoff);
    }
  }

  private void scheduleImportAndWaitUntilIsFinished(EngineImportScheduler scheduler) {
    resetImportBackoff();
    scheduler.scheduleUntilImportIsFinished();
    makeSureAllScheduledJobsAreFinished();
  }

  public void storeImportIndexesToElasticsearch() {
    for (EngineContext engineContext : getConfiguredEngines()) {
      StoreIndexesEngineImportMediator storeIndexesEngineImportJobFactory = (StoreIndexesEngineImportMediator)
          getApplicationContext().getBean(
              BeanHelper.getBeanName(StoreIndexesEngineImportMediator.class),
              engineContext
          );
      storeIndexesEngineImportJobFactory.disableBlocking();

      storeIndexesEngineImportJobFactory.importNextPage();

      makeSureAllScheduledJobsAreFinished();
    }

  }

  private List<EngineContext> getConfiguredEngines() {
    return getApplicationContext().getBean(EngineContextFactory.class).getConfiguredEngines();
  }

  private void makeSureAllScheduledJobsAreFinished() {

    CountDownLatch synchronizationObject = new CountDownLatch(2);
    SynchronizationElasticsearchImportJob importJob =
      new SynchronizationElasticsearchImportJob(synchronizationObject);
    try {
      getElasticsearchImportJobExecutor().executeImportJob(importJob);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    try {
      synchronizationObject.countDown();
      synchronizationObject.await();
    } catch (InterruptedException e) {
      logger.error("interrupted while synchronizing", e);
    }
  }

  public void scheduleImport() {
    for (EngineImportScheduler scheduler : getImportSchedulerFactory().getImportSchedulers()) {
      scheduler.scheduleNextRound();
      makeSureAllScheduledJobsAreFinished();
    }
  }

  private EngineImportSchedulerFactory getImportSchedulerFactory() {
    return getOptimize().getApplicationContext().getBean(EngineImportSchedulerFactory.class);
  }

  private TestEmbeddedCamundaOptimize getOptimize() {
    if (context != null) {
      return TestEmbeddedCamundaOptimize.getInstance(context);
    } else {
      return TestEmbeddedCamundaOptimize.getInstance();
    }
  }

  private ElasticsearchImportJobExecutor getElasticsearchImportJobExecutor() {
    return getOptimize().getElasticsearchImportJobExecutor();
  }

  public void initializeSchema() {
    getOptimize().initializeSchema();
  }

  public OptimizeRequestExecutor getRequestExecutor() {
    return requestExecutor;
  }
  
  protected void starting(Description description) {
    try {
      startOptimize();
      requestExecutor =
        new OptimizeRequestExecutor(
          getOptimize().target(),
          getAuthorizationCookieValue(),
          getApplicationContext().getBean(ObjectMapper.class)
        );
      resetImportStartIndexes();
    } catch (Exception e) {
      //nothing to do here
    }
  }

  public String getAuthenticationToken() {
    return getOptimize().getAuthenticationToken();
  }

  public String getAuthorizationCookieValue() {
    return AuthenticationUtil.createOptimizeAuthCookieValue(getAuthenticationToken());
  }

  public String getNewAuthenticationToken() {
    return getOptimize().getNewAuthenticationToken();
  }

  public String getAuthenticationHeaderForUser(String user, String password) {
    String token = authenticateUser(user, password);
    return "Bearer " + token;
  }

  public String authenticateDemo() {
    Response tokenResponse = authenticateDemoRequest();
    return tokenResponse.readEntity(String.class);
  }

  private Response authenticateDemoRequest() {
    return authenticateUserRequest("demo", "demo");
  }

  public String authenticateUser(String username, String password) {
    Response tokenResponse = authenticateUserRequest(username, password);
    return tokenResponse.readEntity(String.class);
  }

  public Response authenticateUserRequest(String username, String password) {
    CredentialsDto entity = new CredentialsDto();
    entity.setUsername(username);
    entity.setPassword(password);

    return target("authentication")
      .request()
      .post(Entity.json(entity));
  }

  public void startOptimize() throws Exception {
    getOptimize().start();
    getAlertService().init();
    getElasticsearchImportJobExecutor().startExecutingImportJobs();
  }

  protected void finished(Description description) {
    try {
      this.getAlertService().getScheduler().clear();
      TestEmbeddedCamundaOptimize.getInstance().resetConfiguration();
      LocalDateUtil.reset();
      reloadConfiguration();
    } catch (Exception e) {
      logger.error("clean up after test", e);
    }
  }

  public void reloadConfiguration() {
    getOptimize().reloadConfiguration();
  }

  public void stopOptimize() {
    try {
      this.getElasticsearchImportJobExecutor().stopExecutingImportJobs();
    } catch (Exception e) {
      logger.error("Failed to stop elasticsearch import", e);
    }

    try {
      this.getAlertService().destroy();
    } catch (Exception e) {
      logger.error("Failed to destroy alert service", e);
    }

    try {
      getOptimize().destroy();
    } catch (Exception e) {
      logger.error("Failed to stop Optimize", e);
    }
  }

  public final WebTarget target(String path) {
    return getOptimize().target(path);
  }

  public final WebTarget target() {
    return getOptimize().target();
  }

  public final WebTarget rootTarget(String path) {
    return getOptimize().rootTarget(path);
  }

  public final WebTarget rootTarget() {
    return getOptimize().rootTarget();
  }

  public String getProcessDefinitionEndpoint() {
    return getConfigurationService().getProcessDefinitionEndpoint();
  }

  public List<Long> getImportIndexes() {
    List<Long> indexes = new LinkedList<>();

    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      getIndexProvider()
        .getAllEntitiesBasedHandlers(engineAlias)
        .forEach(handler -> indexes.add(handler.getImportIndex()));
      getIndexProvider()
        .getDefinitionBasedHandlers(engineAlias)
        .forEach(handler -> {
          TimestampBasedImportPage page = handler.getNextPage();
          indexes.add(page.getTimestampOfLastEntity().toEpochSecond());
        });
    }

    return indexes;
  }

  public void resetImportStartIndexes() {
    for (ImportIndexHandler importIndexHandler : getIndexProvider().getAllHandlers()) {
      importIndexHandler.resetImportIndex();
    }
  }

  public boolean isImporting() {
    return this.getElasticsearchImportJobExecutor().isActive();
  }

  public ApplicationContext getApplicationContext() {
    return getOptimize().getApplicationContext();
  }

  public DateTimeFormatter getDateTimeFormatter() {
    return getOptimize().getDateTimeFormatter();
  }

  public ConfigurationService getConfigurationService() {
    return getOptimize().getConfigurationService();
  }

  public OptimizeCleanupService getCleanupService() {
    return getOptimize().getCleanupService();
  }

  /**
   * In case the engine got new entities, e.g., process definitions, those are then added to the import index
   */
  public void updateImportIndex() {
    for (String engineAlias : getConfigurationService().getConfiguredEngines().keySet()) {
      if (getIndexProvider().getDefinitionBasedHandlers(engineAlias) != null) {
        for (TimestampBasedImportIndexHandler importIndexHandler : getIndexProvider().getDefinitionBasedHandlers(engineAlias)) {
          importIndexHandler.updateImportIndex();
        }
      }
    }
  }

  private ImportIndexHandlerProvider getIndexProvider() {
    return getApplicationContext().getBean(ImportIndexHandlerProvider.class);
  }

  public AlertService getAlertService() {
    return getApplicationContext().getBean(AlertService.class);
  }


  public ElasticSearchSchemaInitializer getSchemaInitializer() {
    return getApplicationContext().getBean(ElasticSearchSchemaInitializer.class);
  }

  public ElasticSearchSchemaManager getElasticSearchSchemaManager() {
    return getApplicationContext().getBean(ElasticSearchSchemaManager.class);
  }

  public Client getTransportClient() {
    return getApplicationContext().getBean(Client.class);
  }

  public String format(OffsetDateTime offsetDateTime) {
    return this.getDateTimeFormatter().format(offsetDateTime);
  }
}
