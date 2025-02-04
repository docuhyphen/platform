import React from 'react';
import {Navigate} from 'react-router-dom';
import {useAuth} from '../../context/AuthContext';
import MainMenu from "./MainMenu.tsx";

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({ element, path }) => {
    const { token } = useAuth();
    return token ? (
        <div id="asdasdasdasd">
            <MainMenu/>
            <div id="main-section">
                <div>{element}</div>
            </div>
        </div>
    ) : (
        <Navigate to={path}/>
    );
};

export default ProtectedRoute;