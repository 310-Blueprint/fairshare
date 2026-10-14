import { it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import GroupPage from '../src/pages/GroupPage.jsx';
import { exportGroup, getGroup, getGroupBalances, getGroupMembers } from '../src/api/groups';
import { getExpenses, deleteExpense, getExpenseActivity } from '../src/api/expenses';
import { getRecurringExpenses } from '../src/api/recurringExpenses';

vi.mock('../src/api/groups', () => ({
    createGroup: vi.fn(),
    getGroups: vi.fn(),
    getGroup: vi.fn(),
    getGroupMembers: vi.fn(),
    getGroupBalances: vi.fn(),
    computeSettlement: vi.fn(),
    exportGroup: vi.fn(),
}));

vi.mock('../src/api/expenses', () => ({
    getExpenses: vi.fn(),
    deleteExpense: vi.fn(),
    getExpenseActivity: vi.fn(),
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
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    getGroup.mockResolvedValue(GROUP);
    getExpenses.mockResolvedValue({ expenses: [] });
    getExpenseActivity.mockResolvedValue({ activity: [] });
    getRecurringExpenses.mockResolvedValue({ recurringExpenses: [] });
    getGroupBalances.mockResolvedValue([]);
    getGroupMembers.mockResolvedValue({
        members: [
            { userId: 1, username: 'alice', email: 'alice@test.com', netBalance: '0.00', currentUser: true },
        ],
    });
});

afterEach(() => {
    vi.restoreAllMocks();
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

it('#14 AC1: the history keeps the original amount and currency of a foreign expense', async () => {
    getExpenses.mockResolvedValue({
        expenses: [
            {
                id: 3, groupId: 1, paidByUserId: 1, paidByUsername: 'alice',
                amount: '34.11', description: 'Dinner in LA', expenseDate: '2026-08-20',
                createdAt: '2026-08-20T00:00:00Z',
                originalAmount: '20.00', originalCurrency: 'USD', exchangeRate: '1.7056',
            },
        ],
    });

    renderPage();

    expect(await screen.findByText('USD 20.00 (≈ NZD 34.11)')).toBeInTheDocument();
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

it('#12: exporting as CSV downloads the file under the name the server gave it', async () => {
    const blob = new Blob(['Date,Description'], { type: 'text/csv' });
    exportGroup.mockResolvedValue({ blob, filename: 'fairshare-flat-3-2026-10-12.csv' });
    URL.createObjectURL = vi.fn().mockReturnValue('blob:export');
    URL.revokeObjectURL = vi.fn();
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function () {
        expect(this.download).toBe('fairshare-flat-3-2026-10-12.csv');
        expect(this.href).toBe('blob:export');
    });
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Export CSV' }));

    expect(exportGroup).toHaveBeenCalledWith('1', 'csv');
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(click).toHaveBeenCalledTimes(1);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:export');
    click.mockRestore();
});

it('#12: a failed export shows the error and leaves the buttons usable', async () => {
    exportGroup.mockResolvedValue({ error: 'You are not a member of this group' });
    renderPage();

    await userEvent.click(await screen.findByRole('button', { name: 'Export PDF' }));

    expect(exportGroup).toHaveBeenCalledWith('1', 'pdf');
    expect(await screen.findByText('You are not a member of this group')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Export PDF' })).toBeEnabled();
});


it('shows Edit only for expenses created by the current user, regardless of payer', async () => {
    getExpenses.mockResolvedValue({ expenses: [
        { id: 10, createdByUserId: 1, paidByUserId: 2, paidByUsername: 'bob',
          amount: '10.00', description: 'My entry', expenseDate: '2026-10-14' },
        { id: 11, createdByUserId: 2, paidByUserId: 1, paidByUsername: 'alice',
          amount: '20.00', description: 'Their entry', expenseDate: '2026-10-14' },
    ] });
    renderPage();
    const edit = await screen.findByRole('link', { name: 'Edit' });
    expect(edit).toHaveAttribute('href', '/groups/1/expenses/10/edit');
    expect(screen.getAllByRole('link', { name: 'Edit' })).toHaveLength(1);
});


function mockOwnedAndOtherExpenses() {
    getExpenses.mockResolvedValue({ expenses: [
        { id: 10, createdByUserId: 1, paidByUserId: 2, paidByUsername: 'bob',
          amount: '10.00', description: 'My entry', expenseDate: '2026-10-14' },
        { id: 11, createdByUserId: 2, paidByUserId: 1, paidByUsername: 'alice',
          amount: '20.00', description: 'Their entry', expenseDate: '2026-10-14' },
    ] });
}

it('shows Delete only to the creator and removes a successfully deleted expense', async () => {
    mockOwnedAndOtherExpenses();
    deleteExpense.mockResolvedValue({});
    renderPage();
    const button = await screen.findByRole('button', { name: 'Delete My entry' });
    expect(screen.queryByRole('button', { name: 'Delete Their entry' })).not.toBeInTheDocument();
    getGroupMembers.mockResolvedValue({ members: [
        { userId: 1, username: 'alice', netBalance: '7.00', currentUser: true },
    ] });
    await userEvent.click(button);
    expect(deleteExpense).toHaveBeenCalledWith('1', 10);
    expect(await screen.findByText('alice owes NZD 7.00')).toBeInTheDocument();
    expect(screen.queryByText('My entry')).not.toBeInTheDocument();
    expect(screen.getByText('Their entry')).toBeInTheDocument();
});

it('keeps the expense visible when deletion is rejected and displays the server error', async () => {
    mockOwnedAndOtherExpenses();
    deleteExpense.mockResolvedValue({ error: 'Only the creator of this expense can delete it' });
    renderPage();
    await userEvent.click(await screen.findByRole('button', { name: 'Delete My entry' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Only the creator');
    expect(screen.getByText('My entry')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete My entry' })).toBeEnabled();
});


it('AC3: cancelling the confirmation leaves the expense and balances unchanged', async () => {
    mockOwnedAndOtherExpenses();
    window.confirm.mockReturnValue(false);
    renderPage();
    const button = await screen.findByRole('button', { name: 'Delete My entry' });
    const memberLoads = getGroupMembers.mock.calls.length;
    await userEvent.click(button);
    expect(window.confirm).toHaveBeenCalledWith(
        'Delete "My entry"? This will remove the expense and update the group\'s balances.'
    );
    expect(deleteExpense).not.toHaveBeenCalled();
    expect(getGroupMembers).toHaveBeenCalledTimes(memberLoads);
    expect(screen.getByText('My entry')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Delete My entry' })).toBeEnabled();
});

it('AC3: cancelling then explicitly confirming sends only one deletion request', async () => {
    mockOwnedAndOtherExpenses();
    window.confirm.mockReturnValueOnce(false).mockReturnValueOnce(true);
    deleteExpense.mockResolvedValue({});
    renderPage();
    const button = await screen.findByRole('button', { name: 'Delete My entry' });
    await userEvent.click(button);
    expect(deleteExpense).not.toHaveBeenCalled();
    await userEvent.click(button);
    expect(window.confirm).toHaveBeenCalledTimes(2);
    expect(deleteExpense).toHaveBeenCalledTimes(1);
    expect(deleteExpense).toHaveBeenCalledWith('1', 10);
    expect(screen.queryByText('My entry')).not.toBeInTheDocument();
});


it('refreshes group activity after confirmed deletion', async () => {
    mockOwnedAndOtherExpenses();
    deleteExpense.mockResolvedValue({});
    renderPage();
    expect(await screen.findByText('No expense edits or deletions yet.')).toBeInTheDocument();
    getExpenseActivity.mockResolvedValue({ activity: [{
        id: 1, expenseId: 10, expenseDescription: 'My entry', actorUsername: 'alice',
        action: 'DELETED', changedAt: '2026-10-14T08:00:00Z',
    }] });
    await userEvent.click(screen.getByRole('button', { name: 'Delete My entry' }));
    expect(await screen.findByText('alice deleted expense "My entry" (#10)')).toBeInTheDocument();
    expect(screen.queryByText('My entry')).not.toBeInTheDocument();
});

it('an activity loading error leaves the group history visible', async () => {
    mockOwnedAndOtherExpenses();
    getExpenseActivity.mockResolvedValue({ error: 'Activity unavailable' });
    renderPage();
    expect(await screen.findByText('Activity unavailable')).toBeInTheDocument();
    expect(screen.getByText('My entry')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit' })).toBeInTheDocument();
});
