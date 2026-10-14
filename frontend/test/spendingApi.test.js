import { afterEach, expect, it, vi } from 'vitest';
import { getSpendingSummary } from '../src/api/spending';

afterEach(() => {
    vi.unstubAllGlobals();
});

function stubFetch(response) {
    const fetchMock = vi.fn().mockResolvedValue(response);
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

it('asks for the chosen month of the user\'s spending', async () => {
    const summary = { month: '2026-09', total: '20.00' };
    const fetchMock = stubFetch({ ok: true, json: async () => summary });

    const result = await getSpendingSummary(4, '2026-09');

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/users/4/spending?month=2026-09', { credentials: 'include' });
    expect(result).toEqual({ summary });
});

it('leaves out a month that is not yyyy-MM, so the server uses the current month', async () => {
    const fetchMock = stubFetch({ ok: true, json: async () => ({}) });

    await getSpendingSummary(4, '2026-13');

    expect(fetchMock).toHaveBeenCalledWith('http://localhost:8080/users/4/spending', { credentials: 'include' });
});

it('#16 AC4: returns the server\'s error when the overview is refused', async () => {
    stubFetch({ ok: false, status: 403, json: async () => ({ error: 'You can only view your own spending' }) });

    expect(await getSpendingSummary(4, '2026-09')).toEqual({ error: 'You can only view your own spending' });
});

it('rejects a user id that is not a positive integer', async () => {
    await expect(getSpendingSummary('1/../2', '2026-09')).rejects.toThrow('User ID must be a positive integer');
});
