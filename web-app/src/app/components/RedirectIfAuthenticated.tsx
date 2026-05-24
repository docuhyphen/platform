import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";

/**
 * Wraps public auth pages (/sign-in, /sign-up, etc.) and bounces the user
 * away if they're already signed in.
 *
 * Important nuance: during sign-in, `accessToken` is set BEFORE the AppUser
 * has been fetched. If we redirect on `token` alone, the user briefly lands
 * on `/sharing-sessions` (which renders the MainMenu) before SignIn.tsx
 * navigates them on to `/onboarding/individual` — that's the "menu flash"
 * bug. So we wait until we know the user's onboarding state before
 * redirecting, and we redirect to the *correct* landing page directly.
 *
 * State table:
 *   bootstrapping            → splash (cookie refresh probe still in flight)
 *   no token                 → render the wrapped element (e.g. SignIn)
 *   token, no appUser yet    → render the wrapped element (still mid-load)
 *   token + appUser, no person → /onboarding/individual
 *   token + appUser + person → /sharing-sessions
 */
const RedirectIfAuthenticated: React.FC<{ element: React.ReactElement }> = ({element}) =>
{
    const {token, appUser, isBootstrapping} = useAuth();

    // Cookie-based refresh probe still in flight — don't render the public page
    // yet, or the user will see /sign-in flash for a frame before we redirect.
    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    // Not authenticated → show the public page.
    if (!token)
    {
        return element;
    }

    // Authenticated but AppUser hasn't loaded yet → keep the page rendered
    // so the in-flight sign-in flow can finish without a bounce.
    if (!appUser)
    {
        return element;
    }

    // Authenticated and we know the onboarding state → redirect to the
    // correct landing page in a single navigation (no intermediate menu render).
    if (!appUser.person)
    {
        return <Navigate to="/onboarding/individual" replace/>;
    }

    return <Navigate to="/sharing-sessions" replace/>;
};

export default RedirectIfAuthenticated;
