import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';

const RedirectIfAuthenticated: React.FC<{ element: React.ReactElement }> = ({element}) =>
{
    const {token} = useAuth();

    // If user is authenticated, redirect to the landing page
    if (token)
    {
        return <Navigate to="/sharing-sessions" replace/>;
    }

    // Otherwise, render the wrapped element
    return element;
};

export default RedirectIfAuthenticated;
