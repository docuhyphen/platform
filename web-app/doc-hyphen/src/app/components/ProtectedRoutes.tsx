import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import MainMenu from "./MainMenu.tsx";
import {useGlobalStyles} from "../../GlobalStyles";

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({ element, path }) => {
    const { token } = useAuth();

    const styles = useGlobalStyles();

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