import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Landing from '../src/pages/Landing.jsx';
import { getCurrentUser } from '../src/api/users';

vi.mock('../src/api/users', () => ({
    getCurrentUser: vi.fn(),
    updateCurrentUser: vi.fn(),
    logout: vi.fn(),
}));

beforeEach(() => {
    vi.clearAllMocks();
});

it('shows logged-in actions when a session exists', async () => {
    getCurrentUser.mockResolvedValue({ id: 1, username: 'alice' });

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByText('My Groups')).toBeInTheDocument();
});

it('shows sign-up actions when no session exists', async () => {
    getCurrentUser.mockResolvedValue(null);

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByText('Log-in')).toBeInTheDocument();
});
it('#83: shows the three logged-in actions, with the profile left to the header', async () => {
    getCurrentUser.mockResolvedValue({ id: 1, username: 'alice' });

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByRole('link', { name: 'Create Group' })).toHaveAttribute('href', '/groups/new');
    expect(screen.getByRole('link', { name: 'My Groups' })).toHaveAttribute('href', '/groups');
    expect(screen.getByRole('link', { name: 'Individual Debts' })).toHaveAttribute('href', '/debts');
    expect(screen.queryByRole('link', { name: 'My Profile' })).not.toBeInTheDocument();
});

it('#83: welcomes the user under the large logo', async () => {
    getCurrentUser.mockResolvedValue(null);

    render(<MemoryRouter><Landing /></MemoryRouter>);

    expect(await screen.findByRole('heading', { name: 'Welcome to FairShare' })).toBeInTheDocument();
    expect(screen.getByAltText('FairShare logo')).toBeInTheDocument();
});
