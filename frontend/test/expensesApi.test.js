import { afterEach, expect, it, vi } from 'vitest';
import { createExpense } from '../src/api/expenses';

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
