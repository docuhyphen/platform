import React, {useEffect} from 'react';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {useAuth} from '../../../context/AuthContext.tsx';
import {setApiClientAuthToken} from '../../../services/apiClient.ts';
import {fetchAppUser, fetchAppUserPersonOrganization} from '../../../services/appUserApi.ts';
import {Spinner, Text} from "@fluentui/react-components";

const OAuthCallback: React.FC = () =>
{
    const [searchParams] = useSearchParams();
    const {setAccessToken, setIdToken, setAppUser, setAppUserPersonOrganization} = useAuth();
    const navigate = useNavigate();

    useEffect(() =>
    {
        const processCallback = async () =>
        {
            const accessToken = searchParams.get('accessToken');
            const idToken = searchParams.get('idToken');
            const isNewUser = searchParams.get('isNewUser') === 'true';

            if (!accessToken)
            {
                navigate('/sign-in?error=OAuth+authentication+failed');
                return;
            }

            setAccessToken(accessToken);
            if (idToken) setIdToken(idToken);
            setApiClientAuthToken(accessToken);

            if (isNewUser)
            {
                navigate('/onboarding/individual');
                return;
            }

            try
            {
                const appUser = await fetchAppUser(accessToken);
                setAppUser(appUser);

                if (appUser && appUser.person)
                {
                    try
                    {
                        const org = await fetchAppUserPersonOrganization(appUser.id, appUser.person?.id, accessToken);
                        setAppUserPersonOrganization(org);
                    }
                    catch
                    {
                        // Organization not found is okay
                    }
                    navigate('/sharing-sessions');
                }
                else
                {
                    navigate('/onboarding/individual');
                }
            }
            catch
            {
                //ToDo: must probably navigate to an error page because an error can happen for any reason
                // That does not mean that a person is not onboarded
                navigate('/onboarding/individual');
            }
        };

        processCallback();
    }, []);

    return (
        <div style={{display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100vh', gap: '16px'}}>
            <Spinner size="large"/>
            <Text size={400}>Completing sign-in...</Text>
        </div>
    );
};

export default OAuthCallback;

