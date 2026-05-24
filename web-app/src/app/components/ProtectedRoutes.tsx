import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import MainMenu from "./MainMenu.tsx";
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({element, path}) =>
{
    const {token, isBootstrapping} = useAuth();

    // Wait for the cookie-based refresh probe to finish before deciding whether
    // to redirect — otherwise we flash /sign-in for a frame on cold reopen.
    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    return token ? (
        <>
            <MainMenu/>
            {element}
        </>
    ) : (
        <Navigate to={path}/>
    );
};

export default ProtectedRoute;