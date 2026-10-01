import { afterEach, expect, it, vi } from 'vitest';
import { getCurrencies } from '../src/api/currencies';

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

it('returns the supported currencies', async () => {
    const currencies = [
        { code: 'NZD', name: 'New Zealand Dollar' },
        { code: 'USD', name: 'US Dollar' }
    ];
    const fetchMock = respondWith(200, currencies);

    const result = await getCurrencies();

    expect(result).toEqual({ currencies });
    expect(fetchMock).toHaveBeenCalledWith(
        expect.stringMatching(/\/currencies$/),
        expect.objectContaining({ credentials: 'include' })
    );
});

it('reports a failure to load the list', async () => {
    respondWith(500, {});

    const result = await getCurrencies();

    expect(result).toEqual({ error: 'Could not load currencies.' });
});
