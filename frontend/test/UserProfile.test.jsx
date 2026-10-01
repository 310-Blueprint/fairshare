import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import UserProfile from '../src/UserProfile.jsx';
import { getCurrencies } from '../src/api/currencies';

vi.mock('../src/api/currencies', () => ({
    getCurrencies: vi.fn(),
}));

let fetchMock;

beforeEach(() => {
    vi.clearAllMocks();
    getCurrencies.mockResolvedValue({ currencies: [
        { code: 'AUD', name: 'Australian Dollar' },
        { code: 'NZD', name: 'New Zealand Dollar' },
        { code: 'USD', name: 'US Dollar' },
    ] });
    fetchMock = vi.fn().mockResolvedValue({ status: 201, ok: true });
    vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
    vi.unstubAllGlobals();
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/register']}>
            <Routes>
                <Route path="/register" element={<UserProfile />} />
                <Route path="/" element={<p>Landing page</p>} />
            </Routes>
        </MemoryRouter>
    );
}

it('#14: lists every supported currency with its ISO code', async () => {
    renderPage();

    expect(await screen.findByRole('option', { name: 'USD — US Dollar' })).toBeInTheDocument();
    expect([...screen.getByLabelText('Currency').options].map((option) => option.value))
        .toEqual(['', 'AUD', 'NZD', 'USD']);
});

it('#14: choosing a country still fills in its currency, which can then be changed', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole('option', { name: 'USD — US Dollar' });

    await user.selectOptions(screen.getByLabelText('Country'), 'NEW_ZEALAND');
    expect(screen.getByLabelText('Currency')).toHaveValue('NZD');

    await user.selectOptions(screen.getByLabelText('Currency'), 'USD');
    await user.type(screen.getByLabelText('Username'), 'carol');
    await user.type(screen.getByLabelText('Password'), 'password123');
    await user.type(screen.getByLabelText('Email'), 'carol@test.com');
    await user.click(screen.getByRole('button', { name: 'Create Profile' }));

    expect(await screen.findByText('Landing page')).toBeInTheDocument();
    const body = JSON.parse(fetchMock.mock.calls[0][1].body);
    expect(body).toMatchObject({ country: 'NEW_ZEALAND', currency: 'USD' });
});
