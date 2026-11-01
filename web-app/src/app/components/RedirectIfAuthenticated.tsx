import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import AuthBootstrapSplash from "./auth-bootstrap-splash/AuthBootstrapSplash.tsx";

const RedirectIfAuthenticated: React.FC<{ element: React.ReactElement }> = ({element}) =>
{
    const {token, appUser, isBootstrapping} = useAuth();

    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    if (!token)
    {
        return element;
    }

    if (!appUser)
    {
        return element;
    }

    if (!appUser.person)
    {
        return <Navigate to="/onboarding/individual"
                         replace/>;
    }

    return <Navigate to="/exchanges"
                     replace/>;
};

export default RedirectIfAuthenticated;
