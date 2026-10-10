import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AddIndividualDebt from '../src/pages/AddIndividualDebt.jsx';
import { createIndividualDebt } from '../src/api/individualDebts';
import { getCurrencies } from '../src/api/currencies';

vi.mock('../src/api/individualDebts', () => ({
    createIndividualDebt: vi.fn(),
}));

vi.mock('../src/api/currencies', () => ({
    getCurrencies: vi.fn(),
}));

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/debts/new']}>
            <AddIndividualDebt />
        </MemoryRouter>
    );
}

function fillForm({ counterparty = 'bob', amount = '25.00', description = 'Lunch', date = '2026-10-01' } = {}) {
    fireEvent.change(screen.getByLabelText(/who owes you/i), { target: { value: counterparty } });
    fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: amount } });
    fireEvent.change(screen.getByLabelText(/description/i), { target: { value: description } });
    fireEvent.change(screen.getByLabelText(/date/i), { target: { value: date } });
}

describe('AddIndividualDebt', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        getCurrencies.mockResolvedValue({
            currencies: [{ code: 'NZD', name: 'New Zealand Dollar' }, { code: 'USD', name: 'US Dollar' }],
        });
    });

    it.each([
        ['0', 'Amount must be a positive number'],
        ['-5', 'Amount must be a positive number'],
        // type="number" rejects non-numeric characters at the DOM level (in jsdom as in a real
        // browser), so this falls through to the "required" message rather than "must be positive".
        ['abc', 'Amount is required'],
    ])('AC9: rejects an invalid amount (%s) without calling the API', async (amount, message) => {
        renderPage();

        fillForm({ amount });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText(message)).toBeInTheDocument();
        expect(createIndividualDebt).not.toHaveBeenCalled();
    });

    it('AC9: requires a counterparty', async () => {
        renderPage();

        fillForm({ counterparty: '' });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText('Counterparty is required')).toBeInTheDocument();
        expect(createIndividualDebt).not.toHaveBeenCalled();
    });

    it('AC1: submits a valid entry and surfaces a server-side error', async () => {
        createIndividualDebt.mockResolvedValue({ error: 'No matching user was found' });
        renderPage();

        fillForm();
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() => expect(createIndividualDebt).toHaveBeenCalledWith({
            counterpartyIdentifier: 'bob', amount: '25.00', description: 'Lunch', date: '2026-10-01', currency: '',
        }));
        expect(await screen.findByText('No matching user was found')).toBeInTheDocument();
    });

    it('#2: sends the picked currency, defaulting to the home currency', async () => {
        createIndividualDebt.mockResolvedValue({ debt: { id: 1 } });
        renderPage();

        expect(screen.getByLabelText(/currency/i)).toHaveValue('');
        await screen.findByRole('option', { name: /USD/ });
        fillForm();
        fireEvent.change(screen.getByLabelText(/currency/i), { target: { value: 'USD' } });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        await waitFor(() => expect(createIndividualDebt).toHaveBeenCalledWith(
            expect.objectContaining({ currency: 'USD' })));
    });

    it('AC9: shows the server\'s "cannot be yourself" message', async () => {
        createIndividualDebt.mockResolvedValue({ error: 'The counterparty cannot be yourself' });
        renderPage();

        fillForm({ counterparty: 'me@example.com' });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText('The counterparty cannot be yourself')).toBeInTheDocument();
    });
});
