import { beforeEach, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import UserManagement from '../src/pages/UserManagement.jsx';
import { getCurrentUser, updateCurrentUser } from '../src/api/users';
import { getCurrencies } from '../src/api/currencies';

vi.mock('../src/api/users', () => ({
  getCurrentUser: vi.fn(),
  updateCurrentUser: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('../src/api/currencies', () => ({
  getCurrencies: vi.fn(),
}));

const ALICE = {
  id: 42,
  username: 'alice',
  email: 'alice@example.com',
  country: 'NEW_ZEALAND',
  currency: 'NZD'
};

beforeEach(() => {
  vi.clearAllMocks();
  getCurrencies.mockResolvedValue({ currencies: [
    { code: 'AUD', name: 'Australian Dollar' },
    { code: 'NZD', name: 'New Zealand Dollar' },
    { code: 'USD', name: 'US Dollar' },
  ] });
});

function renderPage() {
  render(
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route path="/profile" element={<UserManagement />} />
          <Route path="/login" element={<p>Login page</p>} />
        </Routes>
      </MemoryRouter>
  );
}

it('validates profile updates using the same rules as account creation', async () => {
  const user = userEvent.setup();

  getCurrentUser.mockResolvedValue({
    id: 42,
    username: 'alice',
    email: 'alice@example.com',
    country: 'NEW_ZEALAND',
    currency: 'NZD'
  });

  renderPage();

  expect(await screen.findByText('Manage Profile')).toBeInTheDocument();

  await user.clear(screen.getByLabelText('Username'));
  await user.clear(screen.getByLabelText('Email'));
  await user.selectOptions(screen.getByLabelText('Country'), '');
  await user.selectOptions(screen.getByLabelText('Currency'), '');
  await user.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(await screen.findByText('Username is required')).toBeInTheDocument();
  expect(screen.getByText('Email is required')).toBeInTheDocument();
  expect(screen.getByText('Country is required')).toBeInTheDocument();
  expect(screen.getByText('Currency is required')).toBeInTheDocument();
  expect(updateCurrentUser).not.toHaveBeenCalled();
});

it('redirects to login when the user is not authenticated', async () => {
  getCurrentUser.mockResolvedValue(null);

  renderPage();

  expect(await screen.findByText('Login page')).toBeInTheDocument();
});

it('#14: any supported currency can be chosen as the home currency', async () => {
  const user = userEvent.setup();
  getCurrentUser.mockResolvedValue(ALICE);
  updateCurrentUser.mockResolvedValue({ ...ALICE, currency: 'USD' });

  renderPage();

  expect(await screen.findByRole('option', { name: 'USD — US Dollar' })).toBeInTheDocument();
  expect(screen.getByLabelText('Currency')).toHaveValue('NZD');

  await user.selectOptions(screen.getByLabelText('Currency'), 'USD');
  await user.click(screen.getByRole('button', { name: 'Save Changes' }));

  expect(updateCurrentUser).toHaveBeenCalledWith(expect.objectContaining({ currency: 'USD' }));
});

it('#14: reports when the currency list cannot be loaded', async () => {
  getCurrentUser.mockResolvedValue(ALICE);
  getCurrencies.mockResolvedValue({ error: 'Could not load currencies.' });

  renderPage();

  expect(await screen.findByText('Could not load currencies.')).toBeInTheDocument();
});