import { beforeEach, expect, it, vi } from 'vitest';
import { homeRedirect } from '../src/routes/homeRedirect';
import { getCurrentUser } from '../src/api/users';

vi.mock('../src/api/users', () => ({
    getCurrentUser: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
});

it('#83: sends a signed-in user from the root to their groups', async () => {
    getCurrentUser.mockResolvedValue({ id: 1, username: 'alice' });

    const response = await homeRedirect();

    expect(response.status).toBe(302);
    expect(response.headers.get('Location')).toBe('/groups');
});

it('#83: sends a visitor without a session from the root to log in', async () => {
    getCurrentUser.mockResolvedValue(null);

    expect((await homeRedirect()).headers.get('Location')).toBe('/login');
});

it('#83: treats a failed session check as signed out', async () => {
    getCurrentUser.mockRejectedValue(new Error('offline'));

    expect((await homeRedirect()).headers.get('Location')).toBe('/login');
});
