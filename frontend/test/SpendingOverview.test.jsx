import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import SpendingOverview from '../src/pages/SpendingOverview.jsx';
import { getSpendingSummary } from '../src/api/spending';
import { currentMonth } from '../src/utils/months';

vi.mock('../src/api/spending', () => ({
    getSpendingSummary: vi.fn(),
}));

const EMPTY_HISTORY = ['2026-05', '2026-06', '2026-07', '2026-08', '2026-09', '2026-10']
    .map((month) => ({ month, total: '0.00', changeFromPreviousMonth: null }));

const OCTOBER = {
    month: '2026-10',
    currency: 'NZD',
    total: '65.00',
    byCategory: [
        { key: 'GROCERIES', label: 'Groceries', amount: '30.00' },
        { key: 'TRANSPORT', label: 'Transport', amount: '25.00' },
        { key: 'UNCATEGORISED', label: 'Uncategorised', amount: '10.00' },
    ],
    byGroup: [
        { key: '1', label: 'Flat', amount: '40.00' },
        { key: '2', label: 'Trip', amount: '25.00' },
    ],
    byPerson: [
        { key: '7', label: 'You', amount: '55.00' },
        { key: '8', label: 'bob', amount: '10.00' },
    ],
    months: [
        { month: '2026-05', total: '0.00', changeFromPreviousMonth: null },
        { month: '2026-06', total: '0.00', changeFromPreviousMonth: '0.00' },
        { month: '2026-07', total: '0.00', changeFromPreviousMonth: '0.00' },
        { month: '2026-08', total: '50.00', changeFromPreviousMonth: '50.00' },
        { month: '2026-09', total: '80.00', changeFromPreviousMonth: '30.00' },
        { month: '2026-10', total: '65.00', changeFromPreviousMonth: '-15.00' },
    ],
};

function renderPage() {
    const router = createMemoryRouter(
        [{ path: '/spending', element: <SpendingOverview />, loader: () => ({ id: 7, username: 'alice' }) }],
        { initialEntries: ['/spending'] });
    render(<RouterProvider router={router} />);
}

describe('SpendingOverview', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        getSpendingSummary.mockResolvedValue({ summary: OCTOBER });
    });

    it('AC1: loads the current month of the signed-in user\'s spending and shows its total', async () => {
        renderPage();

        const total = (await screen.findByText('Total for October 2026')).parentElement;
        expect(within(total).getByText('NZD 65.00')).toBeInTheDocument();
        expect(getSpendingSummary).toHaveBeenCalledWith(7, currentMonth());
        expect(screen.getByLabelText('Month')).toHaveValue(currentMonth());
    });

    it('AC1: breaks the total down by category, group and who paid', async () => {
        renderPage();

        const byCategory = await screen.findByRole('region', { name: 'By category' });
        expect(within(byCategory).getByText('Groceries')).toBeInTheDocument();
        expect(within(byCategory).getByText('Uncategorised')).toBeInTheDocument();
        expect(within(byCategory).getByText('NZD 30.00')).toBeInTheDocument();

        const byGroup = screen.getByRole('region', { name: 'By group' });
        expect(within(byGroup).getByText('Trip')).toBeInTheDocument();
        expect(within(byGroup).getByText('NZD 40.00')).toBeInTheDocument();

        const byPerson = screen.getByRole('region', { name: 'By who paid' });
        expect(within(byPerson).getByText('You')).toBeInTheDocument();
        expect(within(byPerson).getByText('bob')).toBeInTheDocument();
    });

    it('AC2: shows each month with an up or down arrow against the month before', async () => {
        renderPage();

        const history = await screen.findByRole('region', { name: 'Month by month' });
        expect(within(history).getByText('Aug 2026')).toBeInTheDocument();
        expect(within(history).getByText('▲ Up NZD 30.00')).toHaveClass('change-up');
        expect(within(history).getByText('▼ Down NZD 15.00')).toHaveClass('change-down');
    });

    it('AC2: the history is not charted until two months have spending', async () => {
        getSpendingSummary.mockResolvedValue({ summary: {
            ...OCTOBER,
            months: EMPTY_HISTORY.map((entry) => (entry.month === '2026-10' ? { ...entry, total: '65.00' } : entry)),
        } });
        renderPage();

        expect(await screen.findByText('Total for October 2026')).toBeInTheDocument();
        expect(screen.queryByRole('region', { name: 'Month by month' })).not.toBeInTheDocument();
    });

    it('AC2: choosing another month loads that month', async () => {
        renderPage();
        await screen.findByText('Total for October 2026');
        getSpendingSummary.mockResolvedValue({ summary: { ...OCTOBER, month: '2026-08', total: '50.00' } });

        // jsdom has no month picker to type into, so the change is fired directly.
        fireEvent.change(screen.getByLabelText('Month'), { target: { value: '2026-08' } });

        expect(await screen.findByText('Total for August 2026')).toBeInTheDocument();
        expect(getSpendingSummary).toHaveBeenLastCalledWith(7, '2026-08');
    });

    it('AC3: a month with no spending shows a message and no charts', async () => {
        getSpendingSummary.mockResolvedValue({ summary: {
            ...OCTOBER, total: '0.00', byCategory: [], byGroup: [], byPerson: [], months: EMPTY_HISTORY,
        } });
        renderPage();

        expect(await screen.findByText('No spending to display for October 2026.')).toBeInTheDocument();
        expect(screen.queryByRole('region')).not.toBeInTheDocument();
        expect(screen.queryByRole('listitem')).not.toBeInTheDocument();
    });

    it('AC4: shows the error when the overview cannot be loaded', async () => {
        getSpendingSummary.mockResolvedValue({ error: 'You can only view your own spending' });
        renderPage();

        expect(await screen.findByText('You can only view your own spending')).toBeInTheDocument();
        expect(screen.queryByRole('region')).not.toBeInTheDocument();
    });
});
