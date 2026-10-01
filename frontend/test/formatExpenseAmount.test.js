import { expect, it } from 'vitest';
import { formatExpenseAmount } from '../src/utils/formatExpenseAmount';

it('shows only the group currency when the expense was entered in it', () => {
    const expense = { amount: '42.5', originalAmount: '42.5', originalCurrency: 'NZD' };

    expect(formatExpenseAmount(expense, 'NZD')).toBe('NZD 42.50');
});

it('shows the original amount and currency first for a foreign expense', () => {
    const expense = { amount: '34.11', originalAmount: '20', originalCurrency: 'USD' };

    expect(formatExpenseAmount(expense, 'NZD')).toBe('USD 20.00 (≈ NZD 34.11)');
});

it('falls back to the group currency when no original currency is known', () => {
    expect(formatExpenseAmount({ amount: '10' }, 'NZD')).toBe('NZD 10.00');
});
