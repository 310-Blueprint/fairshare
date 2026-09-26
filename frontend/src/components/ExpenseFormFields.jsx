// Field groups shared by ExpenseForm and RecurringExpenseForm, which collect the same
// amount/description/payer/participants details for a one-off and a recurring expense.

export function FieldError({ message }) {
    return message ? <span className="error">{message}</span> : null;
}

export function AmountField({ value, onChange, error }) {
    return (
        <div className="form-group">
            <label htmlFor="amount">Amount</label>
            <input
                id="amount"
                type="number"
                step="0.01"
                value={value}
                placeholder="0.00"
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}

export function DescriptionField({ value, onChange, error, placeholder }) {
    return (
        <div className="form-group">
            <label htmlFor="description">Description</label>
            <input
                id="description"
                type="text"
                value={value}
                placeholder={placeholder}
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}

export function PayerSelect({ value, members, onChange, error }) {
    return (
        <div className="form-group">
            <label htmlFor="paidByUserId">Paid by</label>
            <select
                id="paidByUserId"
                value={value}
                onChange={(event) => onChange(event.target.value)}
            >
                {members.map((member) => (
                    <option key={member.userId} value={member.userId}>
                        {member.username}
                    </option>
                ))}
            </select>
            <FieldError message={error} />
        </div>
    );
}

export function ParticipantsChecklist({ participantUserIds, members, onParticipantUserIdsChange, error }) {
    function handleParticipantChange(event) {
        const userId = event.target.value;
        onParticipantUserIdsChange((currentIds) => (
            event.target.checked
                ? [...currentIds, userId]
                : currentIds.filter((id) => id !== userId)
        ));
    }

    return (
        <div className="select-participants">
            <p>Participants</p>
            <ul>
                {members.map((member) => (
                    <li key={member.userId}>
                        <label>
                            <input
                                type="checkbox"
                                value={member.userId}
                                checked={participantUserIds.includes(String(member.userId))}
                                onChange={handleParticipantChange}
                            />
                            {member.username}
                        </label>
                    </li>
                ))}
            </ul>
            <FieldError message={error} />
        </div>
    );
}
