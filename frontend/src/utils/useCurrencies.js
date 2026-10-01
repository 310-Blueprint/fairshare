import { useEffect, useState } from 'react';
import { getCurrencies } from '../api/currencies';

// Loads the supported currencies once, for pages that offer a currency dropdown.
export function useCurrencies() {
    const [currencies, setCurrencies] = useState([]);
    const [currenciesError, setCurrenciesError] = useState('');

    useEffect(() => {
        let active = true;
        getCurrencies()
            .then((result) => {
                if (!active) return;
                if (result.error) {
                    setCurrenciesError(result.error);
                } else {
                    setCurrencies(result.currencies);
                }
            })
            .catch(() => {
                if (active) setCurrenciesError('Could not load currencies.');
            });
        return () => {
            active = false;
        };
    }, []);

    return { currencies, currenciesError };
}
