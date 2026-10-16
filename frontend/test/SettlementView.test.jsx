import { beforeEach, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import SettlementView from '../src/pages/SettlementView';
import { getGroupBalances, computeSettlement, getGroupMembers } from '../src/api/groups';

vi.mock('../src/api/groups', () => ({
    getGroupBalances: vi.fn(), computeSettlement: vi.fn(), getGroupMembers: vi.fn(), markSettlementPaid: vi.fn(),
}));
const OLD_BALANCES = [{ userId: 1, balance: '-20.00' }, { userId: 2, balance: '20.00' }];
const NEW_BALANCES = [{ userId: 1, balance: '-5.00' }, { userId: 2, balance: '5.00' }];
const ZERO_BALANCES = [{ userId: 1, balance: '0.00' }, { userId: 2, balance: '0.00' }];
const plan = amount => [{ fromUserId: 2, toUserId: 1, amount }];

beforeEach(() => {
    vi.clearAllMocks();
    getGroupMembers.mockResolvedValue({ members: [
        { userId: 1, username: 'alice', currentUser: true }, { userId: 2, username: 'bob' },
    ] });
    getGroupBalances.mockResolvedValue(OLD_BALANCES);
    computeSettlement.mockResolvedValue(plan('20.00'));
});

it('reopening the plan loads corrected balances after an expense edit', async () => {
    const first = render(<SettlementView groupId={1} baseCurrency="NZD" />);
    expect(await screen.findByText('bob pays alice: NZD 20.00')).toBeInTheDocument();
    first.unmount();
    getGroupBalances.mockResolvedValue(NEW_BALANCES);
    computeSettlement.mockResolvedValue(plan('5.00'));
    render(<SettlementView groupId={1} baseCurrency="NZD" />);
    expect(await screen.findByText('bob pays alice: NZD 5.00')).toBeInTheDocument();
    expect(screen.queryByText('bob pays alice: NZD 20.00')).not.toBeInTheDocument();
    expect(computeSettlement).toHaveBeenLastCalledWith(1, NEW_BALANCES);
});

it('refreshes the plan on the same page after deletion, including a zero balance result', async () => {
    const { rerender } = render(<SettlementView groupId={1} baseCurrency="NZD" refreshVersion={0} />);
    expect(await screen.findByText('bob pays alice: NZD 20.00')).toBeInTheDocument();
    getGroupBalances.mockResolvedValue(ZERO_BALANCES);
    computeSettlement.mockResolvedValue([]);
    rerender(<SettlementView groupId={1} baseCurrency="NZD" refreshVersion={1} />);
    expect(await screen.findByText('Everyone is settled up.')).toBeInTheDocument();
    expect(screen.queryByText('bob pays alice: NZD 20.00')).not.toBeInTheDocument();
    expect(computeSettlement).toHaveBeenLastCalledWith(1, ZERO_BALANCES);
});

it('Generate fetches fresh balances instead of reusing the previous snapshot', async () => {
    render(<SettlementView groupId={1} baseCurrency="NZD" />);
    expect(await screen.findByText('bob pays alice: NZD 20.00')).toBeInTheDocument();
    getGroupBalances.mockResolvedValue(NEW_BALANCES);
    computeSettlement.mockResolvedValue(plan('5.00'));
    await userEvent.click(screen.getByRole('button', { name: 'Generate settlement plan' }));
    expect(await screen.findByText('bob pays alice: NZD 5.00')).toBeInTheDocument();
    expect(getGroupBalances).toHaveBeenCalledTimes(2);
    expect(computeSettlement).toHaveBeenLastCalledWith(1, NEW_BALANCES);
});

it('ignores an older in-flight plan after a newer refresh finishes', async () => {
    let resolveOldPlan;
    computeSettlement.mockImplementationOnce(() => new Promise(resolve => { resolveOldPlan = resolve; }));
    const { rerender } = render(<SettlementView groupId={1} baseCurrency="NZD" refreshVersion={0} />);
    await vi.waitFor(() => expect(computeSettlement).toHaveBeenCalledTimes(1));
    getGroupBalances.mockResolvedValue(NEW_BALANCES);
    computeSettlement.mockResolvedValue(plan('5.00'));
    rerender(<SettlementView groupId={1} baseCurrency="NZD" refreshVersion={1} />);
    expect(await screen.findByText('bob pays alice: NZD 5.00')).toBeInTheDocument();
    await act(async () => { resolveOldPlan(plan('20.00')); });
    expect(screen.getByText('bob pays alice: NZD 5.00')).toBeInTheDocument();
    expect(screen.queryByText('bob pays alice: NZD 20.00')).not.toBeInTheDocument();
});

it('shows a refresh error instead of displaying a stale plan', async () => {
    render(<SettlementView groupId={1} baseCurrency="NZD" />);
    expect(await screen.findByText('bob pays alice: NZD 20.00')).toBeInTheDocument();
    getGroupBalances.mockRejectedValue(new Error('Unavailable'));
    await userEvent.click(screen.getByRole('button', { name: 'Generate settlement plan' }));
    expect(await screen.findByText('Could not load settlement plan')).toBeInTheDocument();
    expect(screen.queryByText('bob pays alice: NZD 20.00')).not.toBeInTheDocument();
});
