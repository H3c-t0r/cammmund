import React from 'react';
import {mount} from 'enzyme';

import {ThemeProvider} from 'modules/contexts/ThemeContext';
import {ReactComponent as CloseLarge} from 'modules/components/Icon/close-large.svg';

import Modal from './Modal';

const HeaderContent = () => <div>Header Content</div>;
const BodyContent = () => <div>Body Content</div>;
const FooterContent = () => <div>Footer Content</div>;
const onModalClose = jest.fn();

const mountNode = (props = {}) => {
  return mount(
    <ThemeProvider value="dark">
      <Modal {...props} onModalClose={onModalClose} className="modal-root">
        <Modal.Header>
          <HeaderContent />
        </Modal.Header>
        <Modal.Body>
          <BodyContent />
        </Modal.Body>
        <Modal.Footer>
          <FooterContent />
        </Modal.Footer>
      </Modal>
    </ThemeProvider>
  );
};

describe('Modal', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render modal in a div in the document body', () => {
    // given
    const node = mountNode();

    // then
    expect(document.querySelector('.modal-root')).toBeDefined();

    // Header
    expect(node.find(Modal.Header).find(HeaderContent)).toHaveLength(1);
    expect(
      node
        .find(Modal.Header)
        .find("[data-test='cross-button']")
        .find(CloseLarge)
    ).toHaveLength(1);

    // Body
    expect(node.find(Modal.Body).find(BodyContent)).toHaveLength(1);

    // Footer
    expect(node.find(Modal.Footer).find(FooterContent)).toHaveLength(1);
  });

  it('should render a cross close button in the header', () => {
    // given
    const node = mountNode();

    // when
    node
      .find(Modal.Header)
      .find("button[data-test='cross-button']")
      .prop('onClick')();

    // then
    expect(onModalClose).toBeCalled();
  });
});
