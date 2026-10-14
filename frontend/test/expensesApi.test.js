import { afterEach, expect, it, vi } from 'vitest';
import { createExpense, deleteExpense, getExpenseActivity } from '../src/api/expenses';

afterEach(() => {
    vi.unstubAllGlobals();
});

function respondWith(status, body) {
    const fetchMock = vi.fn().mockResolvedValue({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body)
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

it('keeps field messages under the field they belong to', async () => {
    respondWith(400, { amount: 'Amount must be at least 0.01' });

    const result = await createExpense(1, { amount: '0.004' });

    expect(result.errors).toEqual({ amount: 'Amount must be at least 0.01' });
});

it('#14 AC2: shows why the expense was not saved when no exchange rate is available', async () => {
    const message = 'The exchange rate from USD to NZD is unavailable right now, so the expense '
        + 'was not saved. Try again later, or enter the expense in NZD.';
    respondWith(503, { error: message });

    const result = await createExpense(1, { amount: '20', currency: 'USD' });

    expect(result.errors).toEqual({ form: message });
});

it('moves a message that belongs to no field onto the form', async () => {
    respondWith(400, { error: 'Payer must be a member of the group' });

    const result = await createExpense(1, { paidByUserId: 7 });

    expect(result.errors).toEqual({ form: 'Payer must be a member of the group' });
});


it('deletes with session credentials and accepts an empty 204 response', async () => {
    const fetchMock = respondWith(204);
    expect(await deleteExpense(1, 9)).toEqual({});
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/groups/1/expenses/9'), {
        method: 'DELETE', credentials: 'include',
    });
});

it('returns the authorisation error when deletion receives 403', async () => {
    const message = 'Only the creator of this expense can delete it';
    respondWith(403, { error: message });
    expect(await deleteExpense(1, 9)).toEqual({ error: message });
});


it('fetches expense activity with session credentials', async () => {
    const events = [{ id: 1, action: 'DELETED', actorUsername: 'alice' }];
    const fetchMock = respondWith(200, events);
    expect(await getExpenseActivity(1)).toEqual({ activity: events });
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/groups/1/expenses/activity'), {
        credentials: 'include',
    });
});

it('returns the activity authorisation error for a non-member', async () => {
    respondWith(403, { error: 'You must be a group member' });
    expect(await getExpenseActivity(1)).toEqual({ error: 'You must be a group member' });
});
