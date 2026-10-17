import { AmountField, DescriptionField, PayerSelect, ParticipantsChecklist, FieldError } from './ExpenseFormFields';

const FREQUENCY_OPTIONS = [
    { value: 'WEEKLY', label: 'Weekly' },
    { value: 'FORTNIGHTLY', label: 'Fortnightly' },
    { value: 'MONTHLY', label: 'Monthly' },
];

function RecurringExpenseForm({
    amount,
    description,
    paidByUserId,
    frequency,
    startDate,
    endDate,
    participantUserIds,
    members,
    errors,
    submitting,
    onAmountChange,
    onDescriptionChange,
    onPaidByUserIdChange,
    onFrequencyChange,
    onStartDateChange,
    onEndDateChange,
    onParticipantUserIdsChange,
    onSubmit,
}) {
    return (
        <form onSubmit={onSubmit} noValidate>
            <div className="form-row form-row-amount">
                <AmountField value={amount} onChange={onAmountChange} error={errors.amount} />

                <DescriptionField
                    value={description}
                    onChange={onDescriptionChange}
                    error={errors.description}
                    placeholder="What is it for?"
                />
            </div>

            <div className="form-row form-row-3">
                <div className="form-group">
                    <label htmlFor="frequency">Frequency</label>
                    <select
                        id="frequency"
                        value={frequency}
                        onChange={(event) => onFrequencyChange(event.target.value)}
                    >
                        <option value="">Select a frequency</option>
                        {FREQUENCY_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                    <FieldError message={errors.frequency} />
                </div>

                <div className="form-group">
                    <label htmlFor="startDate">Start date</label>
                    <input
                        id="startDate"
                        type="date"
                        value={startDate}
                        onChange={(event) => onStartDateChange(event.target.value)}
                    />
                    <FieldError message={errors.startDate} />
                </div>

                <div className="form-group">
                    <label htmlFor="endDate">End date (optional)</label>
                    <input
                        id="endDate"
                        type="date"
                        value={endDate}
                        onChange={(event) => onEndDateChange(event.target.value)}
                    />
                    <FieldError message={errors.endDate} />
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
                {submitting ? 'Saving...' : 'Save recurring expense'}
            </button>
        </form>
    );
}

export default RecurringExpenseForm;
