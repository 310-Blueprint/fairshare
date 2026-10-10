import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import IndividualDebts from '../src/pages/IndividualDebts.jsx';
import { getBalancesOverview } from '../src/api/individualDebts';

vi.mock('../src/api/individualDebts', () => ({
    getBalancesOverview: vi.fn(),
}));

function renderPage() {
    render(
        <MemoryRouter initialEntries={['/debts']}>
            <IndividualDebts />
        </MemoryRouter>
    );
}

describe('IndividualDebts', () => {
    beforeEach(() => {
        vi.clearAllMocks();
    });

    it('AC5: states who owes whom when the other person owes the current user', async () => {
        getBalancesOverview.mockResolvedValue({
            balances: [
                { counterpartyUserId: 2, counterpartyUsername: 'bob', fromUserId: 2, toUserId: 1, amount: '25.00', currency: 'NZD', settled: false },
            ],
        });

        renderPage();

        expect(await screen.findByText('bob owes you NZD 25.00')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /bob owes you/ })).toHaveAttribute('href', '/debts/2');
    });

    it('AC5: states who owes whom when the current user owes the other person', async () => {
        getBalancesOverview.mockResolvedValue({
            balances: [
                { counterpartyUserId: 3, counterpartyUsername: 'carol', fromUserId: 1, toUserId: 3, amount: '10.50', currency: 'USD', settled: false },
            ],
        });

        renderPage();

        expect(await screen.findByText('You owe carol USD 10.50')).toBeInTheDocument();
    });

    it('AC6: shows settled with no outstanding amount when the net balance is zero', async () => {
        getBalancesOverview.mockResolvedValue({
            balances: [
                { counterpartyUserId: 4, counterpartyUsername: 'dave', fromUserId: null, toUserId: null, amount: '0.00', currency: 'NZD', settled: true },
            ],
        });

        renderPage();

        expect(await screen.findByText('dave is settled up')).toBeInTheDocument();
    });

    it('shows an empty state when there are no balances', async () => {
        getBalancesOverview.mockResolvedValue({ balances: [] });

        renderPage();

        expect(await screen.findByText(/no individual debts yet/i)).toBeInTheDocument();
    });

    it('shows the error returned by the API', async () => {
        getBalancesOverview.mockResolvedValue({ error: 'Could not load your balances.' });

        renderPage();

        expect(await screen.findByText('Could not load your balances.')).toBeInTheDocument();
    });
});
