import { AmountField, CategoryField, CurrencyField, DescriptionField, PayerSelect, ParticipantsChecklist, FieldError } from './ExpenseFormFields';

function ExpenseForm({
    amount,
    currency,
    currencies,
    description,
    category,
    paidByUserId,
    expenseDate,
    participantUserIds,
    members,
    errors,
    submitting,
    onAmountChange,
    onCurrencyChange,
    onDescriptionChange,
    onCategoryChange,
    onPaidByUserIdChange,
    onExpenseDateChange,
    onParticipantUserIdsChange,
    onSubmit,
    maxExpenseDate,
}) {
    return (
        <form onSubmit={onSubmit} noValidate>
            <AmountField value={amount} onChange={onAmountChange} error={errors.amount} />

            <CurrencyField value={currency} currencies={currencies} onChange={onCurrencyChange} error={errors.currency} />

            <DescriptionField
                value={description}
                onChange={onDescriptionChange}
                error={errors.description}
                placeholder="What was it for?"
            />

            <CategoryField value={category} onChange={onCategoryChange} error={errors.category} />

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
