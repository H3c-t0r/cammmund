/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.service.importing.engine.service.ImportService;

import java.time.OffsetDateTime;
import java.util.List;

public abstract class TimestampBasedImportMediator<T extends TimestampBasedImportIndexHandler<?>, DTO>
  extends BackoffImportMediator<T> {

  protected ImportService<DTO> importService;

  protected abstract OffsetDateTime getTimestamp(final DTO dto);

  protected abstract List<DTO> getEntitiesNextPage();

  protected abstract List<DTO> getEntitiesLastTimestamp();

  protected abstract int getMaxPageSize();

  @Override
  protected boolean importNextPage(final Runnable importCompleteCallback) {
    return importNextEnginePageTimestampBased(
      getEntitiesLastTimestamp(), getEntitiesNextPage(), getMaxPageSize(), importCompleteCallback
    );
  }

  protected boolean importNextEnginePageTimestampBased(final List<DTO> entitiesLastTimestamp,
                                                       final List<DTO> entitiesNextPage,
                                                       final int maxPageSize,
                                                       final Runnable importCompleteCallback) {
    boolean timestampNeedsToBeSet = !entitiesNextPage.isEmpty();

    final OffsetDateTime timestamp = timestampNeedsToBeSet ?
      getTimestamp(entitiesNextPage.get(entitiesNextPage.size() - 1)) : null;

    final List<DTO> allEntities = ImmutableList.<DTO>builder()
      .addAll(entitiesLastTimestamp)
      .addAll(entitiesNextPage)
      .build();

    importIndexHandler.updateLastImportExecutionTimestamp();
    if (timestampNeedsToBeSet) {
      importService.executeImport(allEntities, () -> {
        importIndexHandler.updateTimestampOfLastEntity(timestamp);
        importCompleteCallback.run();
      });
      importIndexHandler.updatePendingTimestampOfLastEntity(timestamp);
    } else if (!entitiesLastTimestamp.isEmpty()) {
      importService.executeImport(allEntities, importCompleteCallback);
    } else {
      importCompleteCallback.run();
    }

    return entitiesNextPage.size() >= maxPageSize;
  }

}
