import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

const ProtectedRoute: React.FC<{ element: React.ReactElement, path: string }> = ({ element, path }) => {
    const { token } = useAuth();
    return token ? element : <Navigate to={path} />;
};

export default ProtectedRoute;