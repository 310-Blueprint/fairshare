import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import EditIndividualDebt from '../src/pages/EditIndividualDebt.jsx';
import { getMyIndividualDebts, updateIndividualDebt } from '../src/api/individualDebts';
import { getCurrencies } from '../src/api/currencies';

vi.mock('../src/api/individualDebts', () => ({
    getMyIndividualDebts: vi.fn(),
    updateIndividualDebt: vi.fn(),
}));

vi.mock('../src/api/currencies', () => ({
    getCurrencies: vi.fn(),
}));

const entry = {
    id: 7,
    payerUserId: 1,
    payerUsername: 'alice',
    debtorUserId: 2,
    debtorUsername: 'bob',
    amount: 10,
    currency: 'NZD',
    description: 'Coffee',
    date: '2026-10-01',
    canEdit: true,
};

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/debts/2/entries/7/edit']}>
            <Routes>
                <Route path="/debts/:otherUserId/entries/:entryId/edit" element={<EditIndividualDebt />} />
                <Route path="/debts/:otherUserId" element={<p>Balance page</p>} />
            </Routes>
        </MemoryRouter>
    );
}

describe('EditIndividualDebt', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        getCurrencies.mockResolvedValue({
            currencies: [{ code: 'NZD', name: 'New Zealand Dollar' }, { code: 'USD', name: 'US Dollar' }],
        });
        getMyIndividualDebts.mockResolvedValue({ debts: [entry] });
    });

    it('AC8: fills the form from the entry', async () => {
        renderPage();

        expect(await screen.findByLabelText(/amount/i)).toHaveValue(10);
        expect(screen.getByLabelText(/description/i)).toHaveValue('Coffee');
        await waitFor(() => expect(screen.getByLabelText(/currency/i)).toHaveValue('NZD'));
    });

    it('AC8: saves the edited fields and returns to the balance', async () => {
        updateIndividualDebt.mockResolvedValue({ debt: entry });
        renderPage();

        await screen.findByLabelText(/amount/i);
        fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

        await waitFor(() => expect(updateIndividualDebt).toHaveBeenCalledWith('7', {
            amount: '10', description: 'Coffee', date: '2026-10-01', currency: 'NZD',
        }));
        expect(await screen.findByText('Balance page')).toBeInTheDocument();
    });

    it('AC8: shows who owes whom, but does not let it change', async () => {
        renderPage();

        expect(await screen.findByText('bob owes alice')).toBeInTheDocument();
        expect(screen.queryByLabelText(/who is owed/i)).not.toBeInTheDocument();
    });

    it('AC9: validates on the client before saving', async () => {
        renderPage();

        fireEvent.change(await screen.findByLabelText(/amount/i), { target: { value: '0' } });
        fireEvent.change(screen.getByLabelText(/description/i), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

        expect(await screen.findByText('Amount must be a positive number')).toBeInTheDocument();
        expect(screen.getByText('Description is required')).toBeInTheDocument();
        expect(updateIndividualDebt).not.toHaveBeenCalled();
    });

    it('shows a form-level error from the server', async () => {
        updateIndividualDebt.mockResolvedValue({ error: 'Only the creator of this entry can edit it' });
        renderPage();

        await screen.findByLabelText(/amount/i);
        fireEvent.click(screen.getByRole('button', { name: /save changes/i }));

        expect(await screen.findByText('Only the creator of this entry can edit it')).toBeInTheDocument();
    });

    it('AC7: refuses to edit an entry the user did not create', async () => {
        getMyIndividualDebts.mockResolvedValue({ debts: [{ ...entry, canEdit: false }] });
        renderPage();

        expect(await screen.findByText(/could not be found, or you did not create it/)).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /save changes/i })).not.toBeInTheDocument();
    });
});
