import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import AuthBootstrapSplash from "./AuthBootstrapSplash.tsx";

const NoMenuProtectedRoutes: React.FC<{ element: React.ReactElement, path: string }> = ({element, path}) =>
{
    const {token, isBootstrapping} = useAuth();

    if (isBootstrapping)
    {
        return <AuthBootstrapSplash/>;
    }

    return token ? (
        <>
            {element}
        </>
    ) : (
        <Navigate to={path}/>
    );
};

export default NoMenuProtectedRoutes;