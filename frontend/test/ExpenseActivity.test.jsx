import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import ExpenseActivity from '../src/components/ExpenseActivity';
import { getExpenseActivity } from '../src/api/expenses';

vi.mock('../src/api/expenses', () => ({ getExpenseActivity: vi.fn() }));
const EDIT = { id: 1, expenseId: 9, expenseDescription: 'Dinner', actorUsername: 'alice',
    action: 'EDITED', changedAt: '2026-10-14T08:00:00Z' };
const DELETION = { ...EDIT, id: 2, action: 'DELETED', changedAt: '2026-10-14T09:00:00Z' };

beforeEach(() => {
    vi.clearAllMocks();
    getExpenseActivity.mockResolvedValue({ activity: [] });
});

it('shows who edited or deleted an expense and when, including deleted records', async () => {
    getExpenseActivity.mockResolvedValue({ activity: [DELETION, EDIT] });
    render(<ExpenseActivity groupId={1} />);
    const rows = await screen.findAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(within(rows[0]).getByText('alice deleted expense "Dinner" (#9)')).toBeInTheDocument();
    expect(within(rows[1]).getByText('alice edited expense "Dinner" (#9)')).toBeInTheDocument();
    expect(rows[0].querySelector('time')).toHaveAttribute('datetime', DELETION.changedAt);
    expect(rows[1].querySelector('time')).toHaveAttribute('datetime', EDIT.changedAt);
});

it('refreshes activity after a mutation on the same group page', async () => {
    const { rerender } = render(<ExpenseActivity groupId={1} refreshVersion={0} />);
    expect(await screen.findByText('No expense edits or deletions yet.')).toBeInTheDocument();
    getExpenseActivity.mockResolvedValue({ activity: [DELETION] });
    rerender(<ExpenseActivity groupId={1} refreshVersion={1} />);
    expect(await screen.findByText('alice deleted expense "Dinner" (#9)')).toBeInTheDocument();
    expect(getExpenseActivity).toHaveBeenCalledTimes(2);
});

it('reports activity loading errors', async () => {
    getExpenseActivity.mockResolvedValue({ error: 'Could not load expense activity.' });
    render(<ExpenseActivity groupId={1} />);
    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load expense activity.');
});

it('ignores a late response from a previous group', async () => {
    let resolveFirst;
    getExpenseActivity.mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve; }));
    const { rerender } = render(<ExpenseActivity groupId={1} />);
    rerender(<ExpenseActivity groupId={2} />);
    expect(await screen.findByText('No expense edits or deletions yet.')).toBeInTheDocument();
    resolveFirst({ activity: [DELETION] });
    await waitFor(() => expect(screen.queryByText('alice deleted expense "Dinner" (#9)')).not.toBeInTheDocument());
});
