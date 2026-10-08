// Field groups shared by AddIndividualDebt and EditIndividualDebt, which both collect an
// amount/description/date plus one or two person-identifier fields.
import { FieldError } from './ExpenseFormFields';

export function IdentifierField({ id, label, value, onChange, error, placeholder }) {
    return (
        <div className="form-group">
            <label htmlFor={id}>{label}</label>
            <input
                id={id}
                value={value}
                placeholder={placeholder}
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}

export function DateField({ value, onChange, error, max }) {
    return (
        <div className="form-group">
            <label htmlFor="date">Date</label>
            <input
                id="date"
                type="date"
                value={value}
                max={max}
                onChange={(event) => onChange(event.target.value)}
            />
            <FieldError message={error} />
        </div>
    );
}
