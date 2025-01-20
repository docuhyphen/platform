import React, {createContext, useState, useContext, ReactNode} from 'react';

interface AuthContextType
{
    token: string | null;
    setToken: (token: string | null) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({children}) =>
{

    const [token, setToken] = useState<string | null>(() =>
    {
        return localStorage.getItem('token'); // Initialize from localStorage
    });

    const saveToken = (newToken: string | null) =>
    {
        setToken(newToken);
        if (newToken)
        {
            localStorage.setItem('token', newToken);
        }
        else
        {
            localStorage.removeItem('token');
        }
    };

    return (
        <AuthContext.Provider value={{token, setToken: saveToken}}>
            {children}
        </AuthContext.Provider>
    );

};

export const useAuth = (): AuthContextType =>
{
    const context = useContext(AuthContext);
    if (!context)
    {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};