import React from 'react';
import {shallow} from 'enzyme';

import {mockResolvedAsyncFn, flushPromises} from 'modules/testUtils';
import {
  INSTANCE_STATE,
  ACTIVITY_STATE,
  FLOW_NODE_TYPE,
  PAGE_TITLE
} from 'modules/constants';
import * as api from 'modules/api/instances/instances';
import {getWorkflowName} from 'modules/utils/instance';

import Instance from './Instance';
import Header from './../Header';
import DiagramPanel from './DiagramPanel';
import InstanceDetail from './InstanceDetail';
import InstanceHistory from './InstanceHistory';

const xmlMock = '<foo />';
api.fetchWorkflowXML = mockResolvedAsyncFn(xmlMock);

const INSTANCE = {
  id: '4294980768',
  workflowId: '1',
  startDate: '2018-06-18T08:44:52.240+0000',
  endDate: null,
  state: INSTANCE_STATE.ACTIVE,
  bpmnProcessId: 'demoProcess',
  incidents: [
    {
      id: '4295763008',
      errorType: 'IO_MAPPING_ERROR',
      errorMessage:
        'Could not apply output mappings: Task was completed without payload',
      state: INSTANCE_STATE.ACTIVE,
      activityId: 'taskA',
      activityInstanceId: '4294983744',
      taskId: null
    }
  ],
  activities: [
    {
      activityId: 'foo',
      endDate: '2018-07-16T09:30:56.276Z',
      id: 'foo',
      startDate: '2018-07-16T09:30:56.276Z',
      state: ACTIVITY_STATE.COMPLETED
    },
    {
      activityId: 'taskA',
      endDate: null,
      id: '4294983744',
      startDate: '2018-07-16T09:30:56.276Z',
      state: ACTIVITY_STATE.INCIDENT
    }
  ]
};

// mock api
api.fetchWorkflowInstance = mockResolvedAsyncFn(INSTANCE);

const initialState = {
  instance: null,
  activitiesDetails: {},
  selection: {
    activityInstanceId: null,
    flowNodeId: null
  },
  loaded: false
};

const component = (
  <Instance
    match={{params: {id: INSTANCE.id}, isExact: true, path: '', url: ''}}
  />
);
describe('Instance', () => {
  beforeEach(() => {
    api.fetchWorkflowInstance.mockClear();
  });

  describe('initial state', () => {
    it('should render initially with no data', () => {
      const node = shallow(component);
      expect(node.state()).toEqual(initialState);
      expect(node.text()).toContain('Loading');
    });
  });

  describe('handleActivityInstanceSelection', () => {
    it('should update the state.selection according to the value', () => {
      // given
      const node = shallow(component);
      node.setState({instance: INSTANCE});
      node.update();

      // when
      node.instance().handleActivityInstanceSelection('4294983744');
      node.update();

      // then
      expect(node.state('selection')).toEqual({
        activityInstanceId: '4294983744',
        flowNodeId: 'taskA'
      });
    });
  });

  describe('handleFlowNodeSelection', () => {
    it('should update the state.selection according to the value', () => {
      // given
      const node = shallow(component);
      node.setState({instance: INSTANCE});
      node.update();

      // when
      node.instance().handleFlowNodeSelection('taskA');
      node.update();

      // then
      expect(node.state('selection')).toEqual({
        activityInstanceId: '4294983744',
        flowNodeId: 'taskA'
      });
    });
  });

  describe('data fetching', () => {
    it('should fetch instance information', async () => {
      // given
      shallow(component);

      // then fetching is done with the right id
      expect(api.fetchWorkflowInstance).toHaveBeenCalledTimes(1);
      expect(api.fetchWorkflowInstance.mock.calls[0][0]).toEqual(INSTANCE.id);
    });

    it('should change state after data fetching', async () => {
      // given
      const node = shallow(component);

      // when data fetched
      await flushPromises();
      node.update();

      // then
      expect(node.state('instance')).toEqual(INSTANCE);
      expect(node.state('loaded')).toBe(true);
      expect(document.title).toBe(
        PAGE_TITLE.INSTANCE(INSTANCE.id, getWorkflowName(INSTANCE))
      );
      expect(document.title).toBe(
        `Camunda Operate: Instance ${INSTANCE.id} of Workflow ${getWorkflowName(
          INSTANCE
        )}`
      );
    });
  });

  describe('handleFlowNodesDetailsReady', () => {
    it('should set state.activitiesDetails from given flowNodesDetails', async () => {
      // given
      const node = shallow(component);
      const mockFlowNodesDetails = {
        foo: {name: 'foo', amount: 20}
      };
      const [
        {id, ...firstActivity},
        {id: secondId, ...secondActivity}
      ] = INSTANCE.activities;

      // when
      await flushPromises();
      node.instance().handleFlowNodesDetailsReady(mockFlowNodesDetails);
      node.update();

      // then
      const activitiesDetails = node.state('activitiesDetails');
      expect(activitiesDetails.foo).toBeDefined();
      expect(activitiesDetails.foo).toMatchObject({
        ...mockFlowNodesDetails.foo,
        ...firstActivity
      });
      expect(activitiesDetails[secondId]).toMatchObject({
        ...secondActivity,
        state: ACTIVITY_STATE.INCIDENT
      });
    });
  });

  describe('rendering', () => {
    it('should display a Header, DiagramPanel and Copyright', async () => {
      // given
      const node = shallow(component);
      const ACTIVITIES_DETAILS = {
        foo: {
          activityId: 'foo',
          state: ACTIVITY_STATE.COMPLETED,
          type: FLOW_NODE_TYPE.END_EVENT
        },
        bar: {
          activityId: 'bar',
          state: ACTIVITY_STATE.COMPLETED,
          type: FLOW_NODE_TYPE.TASK
        },
        taskA: {
          activityId: 'taskA',
          state: ACTIVITY_STATE.INCIDENT,
          type: FLOW_NODE_TYPE.TASK
        }
      };
      node.setState({
        instance: INSTANCE,
        activitiesDetails: ACTIVITIES_DETAILS,
        selection: {
          activityInstanceId: '4294983744',
          flowNodeId: 'foo'
        }
      });
      await flushPromises();
      node.update();

      // then
      // Transparent Heading
      expect(node.contains(`Camunda Operate Instance ${INSTANCE.id}`)).toBe(
        true
      );
      // HeaderNode
      const HeaderNode = node.find(Header);
      expect(HeaderNode).toHaveLength(1);
      // Detail in Header
      const DetailNode = HeaderNode.prop('detail');
      expect(DetailNode.type).toBe(InstanceDetail);
      expect(DetailNode.props.instance).toEqual(INSTANCE);
      //DiagramPanel
      const DiagramPanelNode = node.find(DiagramPanel);
      expect(DiagramPanelNode).toHaveLength(1);
      expect(DiagramPanelNode.prop('instance')).toEqual(INSTANCE);
      expect(DiagramPanelNode.prop('selectableFlowNodes')).toEqual([
        'foo',
        'taskA'
      ]);
      expect(DiagramPanelNode.prop('selectedFlowNode')).toBe('foo');
      expect(DiagramPanelNode.prop('onFlowNodeSelected')).toBe(
        node.instance().handleFlowNodeSelection
      );
      expect(DiagramPanelNode.prop('flowNodeStateOverlays')).toEqual([
        {
          id: 'foo',
          state: ACTIVITY_STATE.COMPLETED
        },
        {
          id: 'taskA',
          state: ACTIVITY_STATE.INCIDENT
        }
      ]);

      // InstanceHistory
      const InstanceHistoryNode = node.find(InstanceHistory);
      expect(InstanceHistoryNode).toHaveLength(1);
      expect(InstanceHistoryNode.prop('instance')).toEqual(INSTANCE);
      expect(InstanceHistoryNode.prop('activitiesDetails')).toEqual(
        ACTIVITIES_DETAILS
      );
      expect(InstanceHistoryNode.prop('selectedActivityInstanceId')).toBe(
        '4294983744'
      );
      expect(InstanceHistoryNode.prop('onActivityInstanceSelected')).toBe(
        node.instance().handleActivityInstanceSelection
      );
      // snapshot
      expect(node).toMatchSnapshot();
    });
  });
});
