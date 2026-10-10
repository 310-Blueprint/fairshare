import { afterEach, expect, it, vi } from 'vitest';
import {
    createIndividualDebt,
    deleteIndividualDebt,
    getBalanceWithUser,
    getBalancesOverview,
    getMyIndividualDebts,
    updateIndividualDebt,
} from '../src/api/individualDebts';

afterEach(() => {
    vi.unstubAllGlobals();
});

it('posts a new entry with the counterparty, amount, description and date, defaulting the currency', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetchMock);

    await createIndividualDebt({
        counterpartyIdentifier: 'bob@example.com',
        amount: '25.00',
        description: 'Lunch',
        date: '2026-10-01',
    });

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts',
        {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                counterpartyIdentifier: 'bob@example.com',
                amount: '25.00',
                description: 'Lunch',
                date: '2026-10-01',
                currency: null,
            }),
        });
});

it('posts the chosen currency when one is picked', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetchMock);

    await createIndividualDebt({
        counterpartyIdentifier: 'bob', amount: '25.00', description: 'Lunch', date: '2026-10-01', currency: 'USD',
    });

    expect(JSON.parse(fetchMock.mock.calls[0][1].body).currency).toBe('USD');
});

it('surfaces field errors from a 400 response on create', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({ amount: 'Amount must be at least 0.01' }),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await createIndividualDebt({
        counterpartyIdentifier: 'bob', amount: '0', description: 'x', date: '2026-10-01',
    });

    expect(result.errors).toEqual({ amount: 'Amount must be at least 0.01' });
});

it.each([
    ['create', () => createIndividualDebt({
        counterpartyIdentifier: 'alice', amount: '5', description: 'x', date: '2026-10-01',
    })],
    ['update', () => updateIndividualDebt(1, {
        amount: '5', description: 'x', date: '2026-10-01', currency: 'NZD',
    })],
])('returns a form-level 400 { error } as an error, not field errors, on %s', async (_, send) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: async () => ({ error: 'The counterparty cannot be yourself' }),
    }));

    const result = await send();

    expect(result).toEqual({ error: 'The counterparty cannot be yourself' });
});

it('lists the current user\'s individual debts', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [{ id: 1 }] });
    vi.stubGlobal('fetch', fetchMock);

    const result = await getMyIndividualDebts();

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts', { credentials: 'include' });
    expect(result.debts).toEqual([{ id: 1 }]);
});

it('puts an update with the amount, description, date and currency', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ id: 1 }) });
    vi.stubGlobal('fetch', fetchMock);

    await updateIndividualDebt(1, {
        amount: '15.00', description: 'Dinner', date: '2026-10-02', currency: 'NZD',
    });

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts/1',
        {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({
                amount: '15.00', description: 'Dinner', date: '2026-10-02', currency: 'NZD',
            }),
        });
});

it('deletes an entry by id', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await deleteIndividualDebt(1);

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts/1',
        { method: 'DELETE', credentials: 'include' });
});

it('rejects an unsafe entry id before sending a request', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    await expect(deleteIndividualDebt('../admin')).rejects.toThrow('Debt ID must be a positive integer');
    expect(fetchMock).not.toHaveBeenCalled();
});

it('fetches the balances overview', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => [{ counterpartyUserId: 2 }] });
    vi.stubGlobal('fetch', fetchMock);

    const result = await getBalancesOverview();

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts/balances', { credentials: 'include' });
    expect(result.balances).toEqual([{ counterpartyUserId: 2 }]);
});

it('fetches the net balance with a specific user', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => ({ otherUserId: 2, settled: true }) });
    vi.stubGlobal('fetch', fetchMock);

    const result = await getBalanceWithUser(2);

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/individual-debts/balances/2', { credentials: 'include' });
    expect(result.balance).toEqual({ otherUserId: 2, settled: true });
});
