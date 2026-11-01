import {useState} from 'react';
import {ViewMode} from '../../models/models.tsx';
import {useAuth} from '../../../context/AuthContext.tsx';
import {updateAppUserSettings} from '../../../services/appUserApi';

export const useBlueprintsViewMode = (): [ViewMode, (mode: ViewMode) => Promise<void>] =>
{
    const {appUser, setAppUser, token} = useAuth();
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.blueprintsView ?? 'cards');

    const changeViewMode = async (mode: ViewMode) =>
    {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, blueprintsView: mode};
        await updateAppUserSettings(updated, token)
            .then(() => setAppUser({...appUser, settings: updated}))
            .catch(() => undefined);
    };

    return [viewMode, changeViewMode];
};
