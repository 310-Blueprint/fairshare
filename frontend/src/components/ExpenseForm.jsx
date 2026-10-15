import { AmountField, CurrencyField, DescriptionField, PayerSelect, ParticipantsChecklist, FieldError } from './ExpenseFormFields';

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
            <div className="form-row form-row-amount">
                <AmountField value={amount} onChange={onAmountChange} error={errors.amount} />

                <CurrencyField value={currency} currencies={currencies} onChange={onCurrencyChange} error={errors.currency} />
            </div>

            <div className="form-row form-row-wide-start">
                <DescriptionField
                    value={description}
                    onChange={onDescriptionChange}
                    error={errors.description}
                    placeholder="What was it for?"
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
            </div>

            {/* #83: who paid sits beside who it's split between, so the form fits a laptop screen */}
            <div className="form-row">
                <PayerSelect
                    value={paidByUserId}
                    members={members}
                    onChange={onPaidByUserIdChange}
                    error={errors.paidByUserId}
                />

                <ParticipantsChecklist
                    participantUserIds={participantUserIds}
                    members={members}
                    onParticipantUserIdsChange={onParticipantUserIdsChange}
                    error={errors.participantUserIds}
                />
            </div>

            <FieldError message={errors.form} />

            <button type="submit" disabled={submitting}>
                {submitting ? 'Saving...' : 'Save expense'}
            </button>
        </form>
    );
}

export default ExpenseForm;
