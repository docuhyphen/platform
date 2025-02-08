import React from 'react';
import {useAuth} from '../../context/AuthContext';
import {signOut} from '../../services/api';
import {useNavigate} from 'react-router-dom';
import {Dialog, DialogBody, DialogContent, DialogSurface, DialogTitle, Spinner} from "@fluentui/react-components";

const SignOutButton: React.FC = () =>
{
    const {token, setToken} = useAuth();
    const navigate = useNavigate();
    const [signingOut, setSigningOut] = React.useState(false);
    const [isDialogOpen, setIsDialogOpen] = React.useState(false);

    const handleSignOut = async () =>
    {
        if (signingOut)
        {
            return;
        }

        if (token)
        {
            setSigningOut(true);
            setIsDialogOpen(true);
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
                setIsDialogOpen(false);
            }
        }
    };

    return (
        <>
            <span onClick={handleSignOut}>
                Sign Out
            </span>
            <Dialog open={isDialogOpen}>
                <DialogSurface>
                    <DialogBody>
                        <DialogTitle>Signing Out</DialogTitle>
                        <DialogContent>
                            <Spinner label="Signing out..."/>
                        </DialogContent>
                    </DialogBody>
                </DialogSurface>
            </Dialog>
        </>
    );
};

export default SignOutButton;