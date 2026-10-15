import { afterEach, expect, it, vi } from 'vitest';
import { extractReceipt } from '../src/api/receipts.js';

afterEach(() => {
    vi.unstubAllGlobals();
});

function respondWith(status, body) {
    const fetchMock = vi.fn().mockResolvedValue({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

it('uploads the image as multipart form data with the session cookie', async () => {
    const fetchMock = respondWith(200, { items: [], total: 1 });
    const file = new File(['image'], 'receipt.jpg', { type: 'image/jpeg' });

    await extractReceipt(3, file);

    expect(fetchMock).toHaveBeenCalledOnce();
    const [url, options] = fetchMock.mock.calls[0];
    expect(url).toBe('http://localhost:8080/groups/3/receipts/extract');
    expect(options.method).toBe('POST');
    expect(options.credentials).toBe('include');
    expect(options.body).toBeInstanceOf(FormData);
    expect(options.body.get('file')).toBe(file);
    expect(options.headers).toBeUndefined();
});

it('returns the server failure message for the manual fallback', async () => {
    respondWith(422, { error: 'Receipt was unreadable. Enter it manually.' });

    const result = await extractReceipt(
        3,
        new File(['image'], 'receipt.jpg', { type: 'image/jpeg' })
    );

    expect(result).toEqual({ error: 'Receipt was unreadable. Enter it manually.' });
});
