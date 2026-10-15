import { afterEach, expect, it, vi } from 'vitest';
import { exportGroup, removeGroupMember, markSettlementPaid } from '../src/api/groups';

afterEach(() => {
    vi.unstubAllGlobals();
});

it('constructs the member URL from validated numeric identifiers', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true });
    vi.stubGlobal('fetch', fetchMock);

    await removeGroupMember('5', 2);

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/groups/5/members/2',
        { method: 'DELETE', credentials: 'include' });
});

it('marks a settlement as paid with the correct patch route', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 204 });
    vi.stubGlobal('fetch', fetchMock);

    await markSettlementPaid('5', 2, 3);

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/groups/5/settlements/2/3/paid',
        { method: 'PATCH', credentials: 'include' });
});

it.each([
    ['../admin', 2, 3, 'Group ID must be a positive integer'],
    [5, '../admin', 3, 'From user ID must be a positive integer'],
    [5, 2, Number.MAX_SAFE_INTEGER + 1, 'To user ID must be a positive integer'],
])('rejects unsafe settlement identifiers before sending a request',
    async (groupId, fromUserId, toUserId, message) => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);

        await expect(markSettlementPaid(groupId, fromUserId, toUserId)).rejects.toThrow(message);
        expect(fetchMock).not.toHaveBeenCalled();
    });

it.each([
    ['../admin', 2, 'Group ID must be a positive integer'],
    [5, '../admin', 'Member ID must be a positive integer'],
    [5, Number.MAX_SAFE_INTEGER + 1, 'Member ID must be a positive integer'],
])('rejects unsafe route identifiers before sending a request',
    async (groupId, memberId, message) => {
        const fetchMock = vi.fn();
        vi.stubGlobal('fetch', fetchMock);

        await expect(removeGroupMember(groupId, memberId)).rejects.toThrow(message);
        expect(fetchMock).not.toHaveBeenCalled();
    });

it('#12: exports a group and reads the filename from Content-Disposition', async () => {
    const blob = new Blob(['%PDF']);
    const fetchMock = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({ 'Content-Disposition': 'attachment; filename="fairshare-flat-3-2026-10-12.pdf"' }),
        blob: () => Promise.resolve(blob),
    });
    vi.stubGlobal('fetch', fetchMock);

    const result = await exportGroup('5', 'pdf');

    expect(fetchMock).toHaveBeenCalledWith(
        'http://localhost:8080/groups/5/export?format=pdf',
        { credentials: 'include' });
    expect(result).toEqual({ blob, filename: 'fairshare-flat-3-2026-10-12.pdf' });
});

it('#12: decodes a UTF-8 filename for a non-ASCII group name', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers({
            'Content-Disposition': "attachment; filename=\"=?UTF-8?Q?x?=\"; filename*=UTF-8''fairshare-caf%C3%A9-2026-10-12.csv",
        }),
        blob: () => Promise.resolve(new Blob()),
    }));

    const result = await exportGroup(5, 'csv');

    expect(result.filename).toBe('fairshare-café-2026-10-12.csv');
});

it('#12: falls back to a generic filename when the header is not readable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        headers: new Headers(),
        blob: () => Promise.resolve(new Blob()),
    }));

    const result = await exportGroup(5, 'csv');

    expect(result.filename).toBe('fairshare-export.csv');
});

it('#12: returns the server error for a rejected export', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () => Promise.resolve({ format: 'Unsupported export format: xml. Use csv or pdf' }),
    }));

    const result = await exportGroup(5, 'xml');

    expect(result).toEqual({ error: 'Unsupported export format: xml. Use csv or pdf' });
});
