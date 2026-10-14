import { expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import Header from '../src/components/Header.jsx';

function renderAt(path) {
    render(<MemoryRouter initialEntries={[path]}><Header /></MemoryRouter>);
}

it('#83: links the profile button to the profile page', () => {
    renderAt('/groups');

    expect(screen.getByRole('link', { name: 'My profile' })).toHaveAttribute('href', '/profile');
});

it('#83: shows a home link with the logo away from the home page', () => {
    renderAt('/groups');

    expect(screen.getByRole('link', { name: 'FairShare home' })).toHaveAttribute('href', '/');
});

it('#83: leaves the logo out of the header on the home page, which shows it large', () => {
    renderAt('/');

    expect(screen.queryByRole('link', { name: 'FairShare home' })).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'My profile' })).toBeInTheDocument();
});
