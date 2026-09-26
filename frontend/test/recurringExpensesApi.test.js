import { afterEach, expect, it, vi } from 'vitest';
import { createRecurringExpense, getRecurringExpenses, getRecurringExpense } from '../src/api/recurringExpenses';

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
    respondWith(400, { frequency: 'Frequency is required' });

    const result = await createRecurringExpense(1, { amount: '500.00' });

    expect(result.errors).toEqual({ frequency: 'Frequency is required' });
});

it('moves a message that belongs to no field onto the form', async () => {
    respondWith(400, { error: 'Payer must be a member of the group' });

    const result = await createRecurringExpense(1, { paidByUserId: 7 });

    expect(result.errors).toEqual({ form: 'Payer must be a member of the group' });
});

it('returns the created recurring expense on success', async () => {
    const created = { id: 5, description: 'Rent' };
    respondWith(201, created);

    const result = await createRecurringExpense(1, { description: 'Rent' });

    expect(result.recurringExpense).toEqual(created);
});

it('returns the group\'s recurring expenses on success', async () => {
    const recurringExpenses = [{ id: 5, description: 'Rent' }];
    respondWith(200, recurringExpenses);

    const result = await getRecurringExpenses(1);

    expect(result.recurringExpenses).toEqual(recurringExpenses);
});

it('reports an error when the recurring expense list cannot be loaded', async () => {
    respondWith(403, { error: 'You must be a group member to manage its members' });

    const result = await getRecurringExpenses(1);

    expect(result.error).toEqual('You must be a group member to manage its members');
});

it('returns a single recurring expense on success', async () => {
    const recurringExpense = { id: 5, description: 'Rent' };
    respondWith(200, recurringExpense);

    const result = await getRecurringExpense(1, 5);

    expect(result.recurringExpense).toEqual(recurringExpense);
});
