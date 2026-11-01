import {useState} from 'react';
import {ViewMode} from '../../models/models';
import {useAuth} from '../../../context/AuthContext';
import {updateAppUserSettings} from '../../../services/appUserApi';

export const useCommunicationsViewMode = () =>
{
    const {appUser, setAppUser, token} = useAuth();
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.communicationsView ?? 'cards');

    const handleViewModeChange = async (mode: ViewMode) =>
    {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, communicationsView: mode};
        await updateAppUserSettings(updated, token)
            .then(() => setAppUser({...appUser, settings: updated}))
            .catch(() => undefined);
    };

    return {viewMode, handleViewModeChange};
};
