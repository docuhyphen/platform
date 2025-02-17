import React from 'react';
import {useAuth} from '../../context/AuthContext';
import {signOut} from '../../services/authApi.ts';
import {useNavigate} from 'react-router-dom';

interface SignOutButtonProps
{
    onSignOut: () => void;
}

const SignOutButton: React.FC<SignOutButtonProps> = ({onSignOut}) =>
{
    const {token, setToken} = useAuth();
    const navigate = useNavigate();
    const [signingOut, setSigningOut] = React.useState(false);

    const handleSignOut = async () =>
    {
        if (signingOut)
        {
            return;
        }

        if (token)
        {
            setSigningOut(true);
            onSignOut();
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
            finally
            {
                setSigningOut(false);
            }
        }
    };

    return (
        <span onClick={handleSignOut}>
            Sign Out
        </span>
    );
};

export default SignOutButton;