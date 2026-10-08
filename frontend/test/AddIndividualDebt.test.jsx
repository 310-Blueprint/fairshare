import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import AddIndividualDebt from '../src/pages/AddIndividualDebt.jsx';
import { createIndividualDebt } from '../src/api/individualDebts';

vi.mock('../src/api/individualDebts', () => ({
    createIndividualDebt: vi.fn(),
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
    });

    it('AC9: rejects a zero amount without calling the API', async () => {
        renderPage();

        fillForm({ amount: '0' });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText('Amount must be a positive number')).toBeInTheDocument();
        expect(createIndividualDebt).not.toHaveBeenCalled();
    });

    it('AC9: a non-numeric amount cannot even be typed into the amount field', async () => {
        // type="number" rejects non-numeric characters at the DOM level (in jsdom as in a real
        // browser), so this falls through to the "required" message rather than "must be positive".
        renderPage();

        fillForm({ amount: 'abc' });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText('Amount is required')).toBeInTheDocument();
        expect(createIndividualDebt).not.toHaveBeenCalled();
    });

    it('AC9: rejects a negative amount without calling the API', async () => {
        renderPage();

        fillForm({ amount: '-5' });
        fireEvent.click(screen.getByRole('button', { name: /save/i }));

        expect(await screen.findByText('Amount must be a positive number')).toBeInTheDocument();
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
            counterpartyIdentifier: 'bob', amount: '25.00', description: 'Lunch', date: '2026-10-01',
        }));
        expect(await screen.findByText('No matching user was found')).toBeInTheDocument();
    });
});
