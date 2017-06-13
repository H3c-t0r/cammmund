import {jsx, Children} from 'view-utils';
import {createDiagram} from 'widgets';
import {createDelayedTimePrecisionElement} from 'utils';
import {createHeatmapRendererFunction} from './Heatmap';
import {ProcessInstanceCount} from '../ProcessInstanceCount';
import {hasNoHeatmapData} from '../service';

const Diagram = createDiagram();

export const frequencyDefinition = {
  name: 'Frequency',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(x => x)} />
    <ProcessInstanceCount />
  </Children>,
  hasNoData: hasNoHeatmapData
};

export const durationDefinition = {
  name: 'Duration',
  Diagram: () => <Children>
    <Diagram createOverlaysRenderer={createHeatmapRendererFunction(formatDuration)} />
    <ProcessInstanceCount />
  </Children>,
  hasNoData: hasNoHeatmapData
};

function formatDuration(x) {
  return createDelayedTimePrecisionElement(x, {
    initialPrecision: 2,
    delay: 1500
  });
}
