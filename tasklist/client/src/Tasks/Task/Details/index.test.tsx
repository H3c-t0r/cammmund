/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {Details} from '.';
import {render, screen} from 'modules/testing-library';
import {Route, MemoryRouter, Routes} from 'react-router-dom';
import {MockThemeProvider} from 'modules/theme/MockProvider';
import {nodeMockServer} from 'modules/mockServer/nodeMockServer';
import {http, HttpResponse} from 'msw';
import noop from 'lodash/noop';
import * as taskMocks from 'modules/mock-schema/mocks/task';
import * as userMocks from 'modules/mock-schema/mocks/current-user';
import {useCurrentUser} from 'modules/queries/useCurrentUser';
import {DEFAULT_MOCK_CLIENT_CONFIG} from 'modules/mocks/window';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';

const UserName = () => {
  const {data: currentUser} = useCurrentUser();

  return <div>{currentUser?.displayName}</div>;
};

const getWrapper = (id: string = '0') => {
  const mockClient = getMockQueryClient();

  const Wrapper: React.FC<{
    children?: React.ReactNode;
  }> = ({children}) => (
    <QueryClientProvider client={mockClient}>
      <UserName />
      <MockThemeProvider>
        <MemoryRouter initialEntries={[`/${id}`]}>
          <Routes>
            <Route path="/:id" element={children} />
          </Routes>
        </MemoryRouter>
      </MockThemeProvider>
    </QueryClientProvider>
  );

  return Wrapper;
};

describe('<Details />', () => {
  afterEach(() => {
    window.clientConfig = DEFAULT_MOCK_CLIENT_CONFIG;
  });

  it('should render completed task details', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    render(
      <Details
        task={taskMocks.completedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('My Task')).toBeInTheDocument();
    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Completed by')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('Completion date')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2025 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('No candidates')).toBeInTheDocument();
  });

  it('should render unassigned task details', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('My Task')).toBeInTheDocument();

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(screen.getByText('01 Jan 2024 - 12:00 AM')).toBeInTheDocument();
    expect(screen.getByText('accounting candidate')).toBeInTheDocument();
    expect(screen.getByText('jane candidate')).toBeInTheDocument();
  });

  it('should render unassigned task and assign it', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.patch('/v1/tasks/:taskId/assign', () => {
        return HttpResponse.json(taskMocks.assignedTask('0'));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.unassignedTask('0')}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    await user.click(
      await screen.findByRole('button', {
        name: 'Assign to me',
      }),
    );

    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
    expect(
      await screen.findByText('Assignment successful'),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assigning...')).not.toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.assignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Assignment successful')).not.toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
  });

  it('should render assigned task and unassign it', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
      http.patch('/v1/tasks/:taskId/unassign', () => {
        return HttpResponse.json(taskMocks.unassignedTask('0'));
      }),
    );

    const {user, rerender} = render(
      <Details
        task={taskMocks.assignedTask('0')}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(
      await screen.findByRole('button', {name: /^unassign$/i}),
    ).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();

    await user.click(screen.getByRole('button', {name: /^unassign$/i}));

    expect(
      await screen.findByText('Unassignment successful'),
    ).toBeInTheDocument();

    rerender(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
    );

    expect(
      await screen.findByRole('button', {name: /^assign to me$/i}),
    ).toBeInTheDocument();
    expect(screen.queryByText('Unassigning...')).not.toBeInTheDocument();
    expect(
      screen.queryByText('Unassignment successful'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
  });

  it('should not render assignment button on assigned tasks', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentRestrictedUser);
      }),
      http.patch('/v1/tasks/:taskId/unassign', () => {
        return HttpResponse.json(taskMocks.unassignedTask);
      }),
    );

    render(
      <Details
        task={taskMocks.assignedTask()}
        user={userMocks.currentRestrictedUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Assigned to me')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^unassign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should not render assignment on unassigned tasks', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentRestrictedUser);
      }),
      http.patch('/v1/tasks/:taskId/assign', () => {
        return HttpResponse.json(taskMocks.assignedTask);
      }),
    );

    render(
      <Details
        task={taskMocks.unassignedTask()}
        user={userMocks.currentRestrictedUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Nice Process')).toBeInTheDocument();
    expect(screen.getByText('Unassigned')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', {name: /^assign$/i}),
    ).not.toBeInTheDocument();
  });

  it('should render a task assigned to someone else', async () => {
    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUser);
      }),
    );

    const MOCK_OTHER_ASSIGNEE = 'jane';

    render(
      <Details
        task={{...taskMocks.assignedTask(), assignee: MOCK_OTHER_ASSIGNEE}}
        user={userMocks.currentUser}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByTestId('assignee')).toHaveTextContent(
      `Assigned to ${MOCK_OTHER_ASSIGNEE}`,
    );
  });

  it('should render tenant name', () => {
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };

    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(userMocks.currentUserWithTenants);
      }),
    );

    render(
      <Details
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={userMocks.currentUserWithTenants}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.getByText('Tenant A')).toBeInTheDocument();
  });

  it('should hide tenant name if user only has access to one tenant', () => {
    window.clientConfig = {
      ...DEFAULT_MOCK_CLIENT_CONFIG,
      isMultiTenancyEnabled: true,
    };

    const currentUserWithSingleTenant = {
      ...userMocks.currentUserWithTenants,
      tenants: [userMocks.currentUserWithTenants.tenants[0]],
    };

    nodeMockServer.use(
      http.get('/v1/internal/users/current', () => {
        return HttpResponse.json(currentUserWithSingleTenant);
      }),
    );

    render(
      <Details
        task={{
          ...taskMocks.unassignedTask(),
          tenantId: 'tenantA',
        }}
        user={currentUserWithSingleTenant}
        onAssignmentError={noop}
      />,
      {
        wrapper: getWrapper(),
      },
    );

    expect(screen.queryByText('Tenant A')).not.toBeInTheDocument();
  });
});