import {useCallback, useEffect, useRef, useState} from 'react';
import useToken from '../../context/useToken.tsx';
import {AppUserDetailedDto} from '../models/models.tsx';
import {hasExchangeWorkspaceContent} from './exchangeWorkspaceAvailability.ts';
import {exchangeDeletionObservable, exchangeInitiationObservable} from '../observable/exchangeObservables.ts';

export const useAppUserHasExchanges = (appUser: AppUserDetailedDto | null | undefined, sessionReady: boolean) =>
{
    const token = useToken();
    const [preparingExchanges, setPreparingExchanges] = useState<boolean>(true);
    const [appUserHasExchanges, setAppUserHasExchanges] = useState<boolean>(false);
    const tokenRef = useRef(token);
    tokenRef.current = token;

    const checkAppUserExchanges = useCallback(async () =>
    {
        try
        {
            const hasWorkspaceContent = await hasExchangeWorkspaceContent(tokenRef.current);
            setAppUserHasExchanges(hasWorkspaceContent);
        }
        catch (error)
        {
            console.error("Could not determine whether user has exchanges:", error);
            setAppUserHasExchanges(false);
        }
        finally
        {
            setPreparingExchanges(false);
        }
    }, []);

    useEffect(() =>
    {
        if (!token || !appUser || !sessionReady)
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            if (cancelled) return;
            await checkAppUserExchanges();
        })();

        return () =>
        {
            cancelled = true;
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [appUser?.id, checkAppUserExchanges, sessionReady]);

    useEffect(() =>
    {
        const initiationSubscription = exchangeInitiationObservable.subscribe(() =>
        {
            checkAppUserExchanges();
        });

        const deletionSubscription = exchangeDeletionObservable.subscribe(() =>
        {
            checkAppUserExchanges();
        });

        return () =>
        {
            initiationSubscription.unsubscribe();
            deletionSubscription.unsubscribe();
        };
    }, [checkAppUserExchanges]);

    return {preparingExchanges, appUserHasExchanges};
};
