import { AmountField, DescriptionField, PayerSelect, ParticipantsChecklist, FieldError } from './ExpenseFormFields';

function ExpenseForm({
    amount,
    currency,
    currencies,
    description,
    paidByUserId,
    expenseDate,
    participantUserIds,
    members,
    errors,
    submitting,
    onAmountChange,
    onCurrencyChange,
    onDescriptionChange,
    onPaidByUserIdChange,
    onExpenseDateChange,
    onParticipantUserIdsChange,
    onSubmit,
    maxExpenseDate,
}) {
    return (
        <form onSubmit={onSubmit} noValidate>
            <AmountField value={amount} onChange={onAmountChange} error={errors.amount} />

            {/* #14 AC1, AC3: only supported ISO 4217 codes can be picked */}
            <div className="form-group">
                <label htmlFor="currency">Currency</label>
                <select
                    id="currency"
                    value={currency}
                    onChange={(event) => onCurrencyChange(event.target.value)}
                >
                    {currencies.map((option) => (
                        <option key={option.code} value={option.code}>
                            {option.code} — {option.name}
                        </option>
                    ))}
                </select>
                <FieldError message={errors.currency} />
            </div>

            <DescriptionField
                value={description}
                onChange={onDescriptionChange}
                error={errors.description}
                placeholder="What was it for?"
            />

            <PayerSelect
                value={paidByUserId}
                members={members}
                onChange={onPaidByUserIdChange}
                error={errors.paidByUserId}
            />

            <div className="form-group">
                <label htmlFor="expenseDate">Date</label>
                <input
                    id="expenseDate"
                    type="date"
                    value={expenseDate}
                    max={maxExpenseDate}
                    onChange={(event) => onExpenseDateChange(event.target.value)}
                />
                <FieldError message={errors.expenseDate} />
            </div>

            <ParticipantsChecklist
                participantUserIds={participantUserIds}
                members={members}
                onParticipantUserIdsChange={onParticipantUserIdsChange}
                error={errors.participantUserIds}
            />

            <FieldError message={errors.form} />

            <button type="submit" disabled={submitting}>
                {submitting ? 'Saving...' : 'Save expense'}
            </button>
        </form>
    );
}

export default ExpenseForm;
