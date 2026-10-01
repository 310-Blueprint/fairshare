function money(currency, value) {
    return `${currency} ${Math.abs(Number(value)).toFixed(2)}`;
}

/**
 * #14 AC1: an expense entered in another currency shows what was actually paid, followed by
 * its value in the group's currency, e.g. "USD 20.00 (≈ NZD 34.11)".
 */
export function formatExpenseAmount(expense, baseCurrency) {
    const converted = money(baseCurrency, expense.amount);
    if (!expense.originalCurrency || expense.originalCurrency === baseCurrency) {
        return converted;
    }
    return `${money(expense.originalCurrency, expense.originalAmount)} (≈ ${converted})`;
}
