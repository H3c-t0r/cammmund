import React from 'react';
import {mount} from 'enzyme';

import Dropdown from './Dropdown';

jest.mock('components', () => {
  return {
    Button: ({children}) => <button>{children}</button>,
    Icon: () => <span />
  };
});

jest.mock('./DropdownOption', () => {
  return props => {
    return <button className="DropdownOption">{props.children}</button>;
  };
});

jest.mock('./Submenu', () => props => (
  <div tabIndex="0" className="Submenu">
    Submenu: {JSON.stringify(props)}
  </div>
));

it('should render without crashing', () => {
  mount(<Dropdown />);
});

it('should contain the specified label', () => {
  const node = mount(<Dropdown label="Click me" />);

  expect(node).toIncludeText('Click me');
});

it('should display the child elements when clicking the trigger', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  node.find('.activateButton').simulate('click');

  expect(node.find('.Dropdown')).toMatchSelector('.is-open');
});

it('should close when clicking somewhere', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should close when selecting an option', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>
        <p className="test_option">foo</p>
      </Dropdown.Option>
    </Dropdown>
  );

  node.setState({open: true});

  node.find('.test_option').simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.Dropdown')).not.toMatchSelector('.is-open');
});

it('should set aria-haspopup to true', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.activateButton')).toMatchSelector('.activateButton[aria-haspopup="true"]');
});

it('should set aria-expanded to false by default', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.activateButton')).toMatchSelector('.activateButton[aria-expanded="false"]');
});

it('should set aria-expanded to true when open', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  node.simulate('click');

  expect(node.state('open')).toBe(true);
  expect(node.find('.activateButton')).toMatchSelector('.activateButton[aria-expanded="true"]');
});

it('should set aria-expanded to false when closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  node.setState({open: true});

  node.simulate('click');

  expect(node.state('open')).toBe(false);
  expect(node.find('.activateButton')).toMatchSelector('.activateButton[aria-expanded="false"]');
});

it('should set aria-labelledby on the menu as provided as a prop, amended by "-button"', () => {
  const node = mount(
    <Dropdown id="my-dropdown">
      <Dropdown.Option>foo</Dropdown.Option>
    </Dropdown>
  );

  expect(node.find('.menu')).toMatchSelector('.menu[aria-labelledby="my-dropdown-button"]');
});

it('should close after pressing Esc', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node.setState({open: true});

  node.simulate('keyDown', {key: 'Escape', keyCode: 27, which: 27});

  expect(node.state('open')).toBe(false);
});

it('should not change focus after pressing an arrow key if closed', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node
    .find('button')
    .first()
    .getDOMNode()
    .focus();

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('Click me');
});

it('should change focus after pressing an arrow key if opened', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Option>foo</Dropdown.Option>
      <Dropdown.Option>bar</Dropdown.Option>
    </Dropdown>
  );

  node
    .find('button')
    .first()
    .getDOMNode()
    .focus();

  node.instance().setState({open: true});

  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('foo');
  node.simulate('keyDown', {key: 'ArrowDown'});
  expect(document.activeElement.textContent).toBe('bar');
});

it('should pass open, offset, setOpened, setClosed, forceToggle and closeParent properties to submenus', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
    </Dropdown>
  );

  const submenuNode = node.find(Dropdown.Submenu);
  expect(submenuNode).toHaveProp('open');
  expect(submenuNode).toHaveProp('offset');
  expect(submenuNode).toHaveProp('setOpened');
  expect(submenuNode).toHaveProp('setClosed');
  expect(submenuNode).toHaveProp('forceToggle');
  expect(submenuNode).toHaveProp('closeParent');
});

it('should open a submenu when it is opened', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.setState({openSubmenu: 0});

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', true);
});

it('should not open a submenu when it is opened, but another is forced open', () => {
  const node = mount(
    <Dropdown>
      <Dropdown.Submenu />
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.setState({openSubmenu: 0, fixedSubmenu: 1});

  expect(node.find(Dropdown.Submenu).at(0)).toHaveProp('open', false);
  expect(node.find(Dropdown.Submenu).at(1)).toHaveProp('open', true);
});

it('should open a submenu when pressing the right arrow on a submenu entry', () => {
  const node = mount(
    <Dropdown label="Click me">
      <Dropdown.Submenu />
    </Dropdown>
  );

  node.instance().setState({open: true});
  node
    .find(Dropdown.Submenu)
    .first()
    .getDOMNode()
    .focus();

  node.simulate('keyDown', {key: 'ArrowRight'});

  expect(node.state('fixedSubmenu')).toBe(0);
});
