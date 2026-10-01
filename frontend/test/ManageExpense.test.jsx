import { it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import ManageExpense from '../src/pages/ManageExpense.jsx';
import { getGroupMembers } from '../src/api/groups';
import { getExpense, updateExpense } from '../src/api/expenses';
import { getCurrencies } from '../src/api/currencies';

vi.mock('../src/api/groups', () => ({
    getGroupMembers: vi.fn(),
}));

vi.mock('../src/api/expenses', () => ({
    getExpense: vi.fn(),
    updateExpense: vi.fn(),
}));

vi.mock('../src/api/currencies', () => ({
    getCurrencies: vi.fn(),
}));

const MEMBERS = [
    { userId: 1, username: 'alice', email: 'alice@test.com' },
    { userId: 2, username: 'bob', email: 'bob@test.com' },
    { userId: 3, username: 'carol', email: 'carol@test.com' },
];

beforeEach(() => {
    vi.clearAllMocks();
    getGroupMembers.mockResolvedValue({ members: MEMBERS });
    getCurrencies.mockResolvedValue({ currencies: [
        { code: 'NZD', name: 'New Zealand Dollar' },
        { code: 'USD', name: 'US Dollar' },
    ] });
    getExpense.mockResolvedValue({ expense: {
        id: 9,
        amount: '42.50',
        description: 'Groceries',
        paidByUserId: 2,
        expenseDate: '2024-06-15',
        participantUserIds: [1, 2],
        originalAmount: '42.50',
        originalCurrency: 'NZD',
    } });
    updateExpense.mockResolvedValue({ expense: { id: 9 } });
});

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/groups/1/expenses/9/edit']}>
            <Routes>
                <Route path="/groups/:id/expenses/:expenseId/edit" element={<ManageExpense />} />
                <Route path="/groups/:id" element={<h1>Flat 3</h1>} />
            </Routes>
        </MemoryRouter>
    );
}

it('preselects the expense participants', async () => {
    renderPage();

    expect(await screen.findByRole('checkbox', { name: 'alice' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'bob' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'carol' })).not.toBeChecked();
});

it('#14 AC1: edits the amount and currency that were originally entered', async () => {
    getExpense.mockResolvedValue({ expense: {
        id: 9,
        amount: '34.11',
        description: 'Dinner',
        paidByUserId: 1,
        expenseDate: '2024-06-15',
        participantUserIds: [1, 2],
        originalAmount: '20.00',
        originalCurrency: 'USD',
    } });
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByLabelText('Amount')).toHaveValue(20);
    expect(screen.getByLabelText('Currency')).toHaveValue('USD');

    await user.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(updateExpense).toHaveBeenCalledWith('1', '9', expect.objectContaining({
        amount: '20.00',
        currency: 'USD',
    }));
});

it('#14 AC2: an unavailable rate is reported and the edit is not left', async () => {
    const message = 'The exchange rate from USD to NZD is unavailable right now, so the expense '
        + 'was not saved. Try again later, or enter the expense in NZD.';
    updateExpense.mockResolvedValue({ errors: { form: message } });
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByLabelText('Currency'), 'USD');
    await user.click(screen.getByRole('button', { name: 'Save expense' }));

    expect(await screen.findByText(message)).toBeInTheDocument();
    expect(screen.queryByText('Flat 3')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Currency')).toHaveValue('USD');
});

it('AC3: split across a subset of members', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.selectOptions(await screen.findByLabelText('Paid by'), '2');
    // Alice and Bob are the saved participants; add Carol to change the split.
    await user.click(screen.getByLabelText('carol'));
    await user.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(updateExpense).toHaveBeenCalledWith('1', '9', {
        amount: '42.50',
        currency: 'NZD',
        description: 'Groceries',
        paidByUserId: 2,
        expenseDate: '2024-06-15',
        participantUserIds: [1, 2, 3],
    });
});

it('AC6: at least one participant must be selected', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.type(await screen.findByLabelText('Amount'), '42.50');
    await user.type(screen.getByLabelText('Description'), 'Groceries');
    await user.selectOptions(screen.getByLabelText('Paid by'), '2');
    await user.click(screen.getByLabelText('alice'));
    await user.click(screen.getByLabelText('bob'));
    await user.click(screen.getByRole('button', { name: 'Save expense' }));
    expect(await screen.findByText('At least one participant must be selected.')).toBeInTheDocument();
    expect(updateExpense).not.toHaveBeenCalled();
});