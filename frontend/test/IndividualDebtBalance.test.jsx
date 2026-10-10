import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import IndividualDebtBalance from '../src/pages/IndividualDebtBalance.jsx';
import { deleteIndividualDebt, getBalanceWithUser } from '../src/api/individualDebts';

vi.mock('../src/api/individualDebts', () => ({
    deleteIndividualDebt: vi.fn(),
    getBalanceWithUser: vi.fn(),
}));

const mine = {
    id: 7, payerUserId: 1, payerUsername: 'alice', debtorUserId: 2, debtorUsername: 'bob',
    amount: 10, currency: 'USD', description: 'Tickets', date: '2026-10-01', canEdit: true,
};
const theirs = {
    id: 8, payerUserId: 2, payerUsername: 'bob', debtorUserId: 1, debtorUsername: 'alice',
    amount: 4, currency: 'NZD', description: 'Coffee', date: '2026-10-02', canEdit: false,
};

function balance(entries) {
    return {
        otherUserId: 2, otherUsername: 'bob', fromUserId: 2, toUserId: 1,
        amount: 13, currency: 'NZD', settled: false, entries,
    };
}

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/debts/2']}>
            <Routes>
                <Route path="/debts/:otherUserId" element={<IndividualDebtBalance />} />
            </Routes>
        </MemoryRouter>
    );
}

describe('IndividualDebtBalance', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        getBalanceWithUser.mockResolvedValue({ balance: balance([mine, theirs]) });
    });

    it('AC5: states the net balance in the viewer\'s currency, and each entry in its own', async () => {
        renderPage();

        expect(await screen.findByText('bob owes you NZD 13.00')).toBeInTheDocument();
        expect(screen.getByText('alice is owed USD 10.00 by bob')).toBeInTheDocument();
        expect(screen.getByText('bob is owed NZD 4.00 by alice')).toBeInTheDocument();
    });

    it('AC7: only shows Edit and Delete on entries the user created', async () => {
        renderPage();

        const mineRow = (await screen.findByText(/USD 10.00/)).closest('li');
        const theirsRow = screen.getByText(/NZD 4.00/).closest('li');

        expect(within(mineRow).getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/debts/2/entries/7/edit');
        expect(within(mineRow).getByRole('button', { name: 'Delete' })).toBeInTheDocument();
        expect(within(theirsRow).queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
        expect(within(theirsRow).queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('asks for confirmation before deleting, and does nothing on cancel', async () => {
        const user = userEvent.setup();
        renderPage();

        await user.click(await screen.findByRole('button', { name: 'Delete' }));
        const dialog = screen.getByRole('dialog', { name: 'Delete this entry?' });
        await user.click(within(dialog).getByRole('button', { name: 'Cancel' }));

        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(deleteIndividualDebt).not.toHaveBeenCalled();
    });

    it('deletes once confirmed and refreshes the balance', async () => {
        const user = userEvent.setup();
        deleteIndividualDebt.mockResolvedValue({});
        renderPage();

        await user.click(await screen.findByRole('button', { name: 'Delete' }));
        getBalanceWithUser.mockResolvedValue({ balance: { ...balance([theirs]), amount: 4, fromUserId: 1, toUserId: 2 } });
        await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }));

        expect(deleteIndividualDebt).toHaveBeenCalledWith(7);
        expect(await screen.findByText('You owe bob NZD 4.00')).toBeInTheDocument();
        expect(screen.queryByText(/USD 10.00/)).not.toBeInTheDocument();
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('keeps the dialog open with the error when deleting fails', async () => {
        const user = userEvent.setup();
        deleteIndividualDebt.mockResolvedValue({ error: 'Could not delete this entry.' });
        renderPage();

        await user.click(await screen.findByRole('button', { name: 'Delete' }));
        await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }));

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not delete this entry.');
        expect(screen.getByRole('dialog')).toBeInTheDocument();
    });

    it('AC6: shows settled when the net balance is zero', async () => {
        getBalanceWithUser.mockResolvedValue({
            balance: { ...balance([]), settled: true, amount: 0, fromUserId: null, toUserId: null },
        });
        renderPage();

        expect(await screen.findByText('You and bob are settled up')).toBeInTheDocument();
        expect(screen.getByText(/this figure is from shared groups only/)).toBeInTheDocument();
    });
});
