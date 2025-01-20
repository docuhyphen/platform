import React from 'react';
import {useAuth} from '../../context/AuthContext';
import {signOut} from '../../services/api';
import {useNavigate} from 'react-router-dom';
import {Button} from "@fluentui/react-components";

const SignOutButton: React.FC = () =>
{
    const {token, setToken} = useAuth();
    const navigate = useNavigate();

    const handleSignOut = async () =>
    {
        if (token)
        {
            try
            {
                await signOut(token);
                setToken(null);
                navigate('/sign-in');
            }
            catch (error)
            {
                console.error('Sign out failed:', error);
            }
        }
    };

    return <Button onClick={handleSignOut}> Sign Out </Button>;
};

export default SignOutButton;