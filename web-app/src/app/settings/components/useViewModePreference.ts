import {useState} from 'react';
import {useAuth} from '../../../context/AuthContext';
import {updateAppUserSettings} from '../../../services/appUserApi';
import {ViewMode} from '../../models/models';

type ViewModeSettingKey = 'sequencesView' | 'variablesView';

export const useViewModePreference = (settingKey: ViewModeSettingKey) =>
{
    const {appUser, setAppUser, token} = useAuth();
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.[settingKey] ?? 'cards');

    const changeViewMode = async (mode: ViewMode) =>
    {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, [settingKey]: mode};
        try
        {
            await updateAppUserSettings(updated, token);
            if (appUser) setAppUser({...appUser, settings: updated});
        }
        catch
        {
            return;
        }
    };

    return {viewMode, changeViewMode};
};
