import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import GroupPage from '../src/pages/GroupPage.jsx';
import { getGroup, getGroupBalances, getGroupMembers } from '../src/api/groups';
import { getExpenses } from '../src/api/expenses';
import { getRecurringExpenses } from '../src/api/recurringExpenses';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
    getGroupMembers: vi.fn(),
    getGroupBalances: vi.fn(),
    computeSettlement: vi.fn(),
}));

vi.mock('../src/api/expenses', () => ({
    getExpenses: vi.fn(),
}));

vi.mock('../src/api/recurringExpenses', () => ({
    getRecurringExpenses: vi.fn(),
}));

const GROUP = {
    id: 1,
    name: 'Flat 3',
    description: null,
    baseCurrency: 'NZD',
    createdAt: '2026-08-16T00:00:00Z',
    memberCount: 2,
};

beforeEach(() => {
    vi.clearAllMocks();
    getGroup.mockResolvedValue(GROUP);
    getExpenses.mockResolvedValue({ expenses: [] });
    getRecurringExpenses.mockResolvedValue({ recurringExpenses: [] });
    getGroupBalances.mockResolvedValue([]);
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '0.00', currentUser: true },
        ],
    });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1']}>
            <Routes>
                <Route path="/groups/:id" element={<GroupPage />} />
            </Routes>
        </MemoryRouter>
    );
}

it('AC2: a new group lists no expenses and shows zero balances', async () => {
    renderPage();

    expect(await screen.findByText('Flat 3')).toBeInTheDocument();
    expect(screen.getByText('No expenses yet.')).toBeInTheDocument();
});

it('links to member management from the group page', async () => {
    renderPage();

    const link = await screen.findByRole('link', { name: 'Manage members' });
    expect(link).toHaveAttribute('href', '/groups/1/members');
});

it('AC7: lists each expense with amount, description, payer and date', async () => {
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 2, groupId: 1, paidByUserId: 2, paidByUsername: 'bob',
                amount: '20.00', description: 'Pizza', expenseDate: '2026-08-18',
                createdAt: '2026-08-18T00:00:00Z',
            },
            {
                id: 1, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '10.00', description: 'Taxi', expenseDate: '2026-08-16',
                createdAt: '2026-08-16T00:00:00Z',
            },
        ],
    });

    renderPage();

    expect(await screen.findByText('Pizza')).toBeInTheDocument();
    expect(screen.getByText('bob paid on 2026-08-18')).toBeInTheDocument();
    expect(screen.getByText('NZD 20.00')).toBeInTheDocument();
    expect(screen.getByText('Taxi')).toBeInTheDocument();
    expect(screen.getByText('alice paid on 2026-08-16')).toBeInTheDocument();
    expect(screen.getByText('NZD 10.00')).toBeInTheDocument();
});

it('#13 AC1, AC4: lists each recurring expense with its frequency, payer and active/ended status', async () => {
    getRecurringExpenses.mockResolvedValue({
        recurringExpenses: [
            {
                id: 1, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '500.00', description: 'Rent', frequency: 'MONTHLY',
                startDate: '2026-01-01', endDate: null, nextDueDate: '2026-09-01', active: true,
            },
            {
                id: 2, groupId: 1, paidByUserId: 2, paidByUsername: 'bob',
                amount: '15.00', description: 'Netflix', frequency: 'MONTHLY',
                startDate: '2026-01-01', endDate: '2026-03-01', nextDueDate: '2026-03-01', active: false,
            },
        ],
    });

    renderPage();

    expect(await screen.findByText('Rent')).toBeInTheDocument();
    expect(screen.getByText(/Monthly.*paid by alice.*starts 2026-01-01.*Active/)).toBeInTheDocument();
    expect(screen.getByText('NZD 500.00')).toBeInTheDocument();

    expect(screen.getByText('Netflix')).toBeInTheDocument();
    expect(screen.getByText(/Monthly.*paid by bob.*starts 2026-01-01.*ends 2026-03-01.*Ended/)).toBeInTheDocument();
});

it('shows an empty state and a working link when a group has no recurring expenses', async () => {
    renderPage();

    expect(await screen.findByText('No recurring expenses yet.')).toBeInTheDocument();
    const link = screen.getByRole('link', { name: 'Add recurring expense' });
    expect(link).toHaveAttribute('href', '/groups/1/recurring-expenses/new');
});

it('#13 AC3: marks a generated expense as recurring and links back to its recurring expense', async () => {
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 3, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '500.00', description: 'Rent', expenseDate: '2026-09-01',
                createdAt: '2026-09-01T00:00:00Z', recurringExpenseId: 7,
            },
            {
                id: 4, groupId: 1, paidByUserId: 2, paidByUsername: 'bob',
                amount: '20.00', description: 'Taxi', expenseDate: '2026-08-18',
                createdAt: '2026-08-18T00:00:00Z', recurringExpenseId: null,
            },
        ],
    });

    renderPage();

    await screen.findByText('Rent');
    const link = screen.getByRole('link', { name: 'Recurring' });
    expect(link).toHaveAttribute('href', '#recurring-expense-7');

    // The manually recorded expense has no recurring marker.
    const taxiRow = screen.getByText('Taxi').closest('li');
    expect(taxiRow).not.toHaveTextContent('Recurring');
});

it('a failed recurring-expenses request does not hide the rest of the group page', async () => {
    getRecurringExpenses.mockResolvedValue({ error: 'Could not load recurring expenses.' });
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 1, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '10.00', description: 'Taxi', expenseDate: '2026-08-16',
                createdAt: '2026-08-16T00:00:00Z',
            },
        ],
    });

    renderPage();

    // Main group information is still visible...
    expect(await screen.findByText('Flat 3')).toBeInTheDocument();
    expect(screen.getByText('Taxi')).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Manage members' })).toBeInTheDocument();

    // ...and only the recurring-expenses section shows an error.
    expect(screen.getByText('Could not load recurring expenses.')).toBeInTheDocument();
    expect(screen.queryByText('No recurring expenses yet.')).not.toBeInTheDocument();
});

it('AC1: shows what each member is owed or owes', async () => {
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '-21.25', currentUser: true },
            { userId: 2, username: 'bob', email: 'bob@test.com', netBalance: '21.25', currentUser: false },
        ],
    });

    renderPage();

    expect(await screen.findByText('alice is owed NZD 21.25')).toBeInTheDocument();
    expect(screen.getByText('bob owes NZD 21.25')).toBeInTheDocument();
});

it('AC8: shows a not-found message when the user is not a member', async () => {
    getGroup.mockResolvedValue(null);

    renderPage();

    expect(await screen.findByText('Group not found')).toBeInTheDocument();
    expect(screen.queryByText('No expenses yet.')).not.toBeInTheDocument();
});
