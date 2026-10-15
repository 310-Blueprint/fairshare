import { redirect } from 'react-router-dom';
import { getCurrentUser } from '../api/users.js';

/**
 * #83: there is no home page. The root sends a signed-in user to their groups and
 * everyone else to log in.
 */
export async function homeRedirect() {
    const user = await getCurrentUser().catch(() => null);
    return redirect(user ? '/groups' : '/login');
}
