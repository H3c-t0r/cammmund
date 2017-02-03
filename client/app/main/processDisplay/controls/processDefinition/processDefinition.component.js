import {jsx, withSelector, Scope, List, Text, Attribute, OnEvent} from 'view-utils';
import {loadProcessDefinitions, selectProcessDefinition} from './processDefinition.service';
import {INITIAL_STATE} from 'utils/loading';

export const ProcessDefinition = withSelector(ProcessDefinitionComponent);

export function ProcessDefinitionComponent() {
  const template = <td>
    <select className="form-control">
      <OnEvent event="change" listener={select} />
      <option disabled="" value="">Select Process</option>
      <Scope selector={getAvailableDefinitions}>
        <List>
          <option>
            <Attribute selector="id" attribute="value" />
            <Text property="name" />
          </option>
        </List>
      </Scope>
    </select>
  </td>;

  function select({node}) {
    selectProcessDefinition(node.value);
  }

  function getAvailableDefinitions({availableProcessDefinitions}) {
    return availableProcessDefinitions.data;
  }

  function update(parentNode, eventsBus) {
    const templateUpdate = template(parentNode, eventsBus);

    return [templateUpdate, ({availableProcessDefinitions, selected}) => {
      parentNode.querySelector('select').value = selected || '';

      if (availableProcessDefinitions.state === INITIAL_STATE) {
        loadProcessDefinitions();
      }
    }];
  }

  return update;
}
