import {useState} from 'react';
import {ViewMode} from '../../models/models.tsx';
import {updateAppUserSettings} from '../../../services/appUserApi';
import {useAuth} from '../../../context/AuthContext.tsx';

export const useDocumentLibraryViewMode = () =>
{
    const {appUser, setAppUser, token} = useAuth();
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.documentLibraryView ?? 'cards');

    const handleViewModeChange = async (mode: ViewMode) =>
    {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, documentLibraryView: mode};
        const saved = await updateAppUserSettings(updated, token)
            .then(() => true)
            .catch(() => false);
        if (saved) setAppUser({...appUser, settings: updated});
    };

    return {viewMode, handleViewModeChange};
};
