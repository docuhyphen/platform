import {useState} from "react";
import {useAuth} from "../../../../context/AuthContext";
import {updateAppUserSettings} from "../../../../services/appUserApi";
import {ViewMode} from "../../../models/models.tsx";

export const useWorkflowsViewMode = () =>
{
    const {appUser, setAppUser, token} = useAuth();
    const [viewMode, setViewMode] = useState<ViewMode>(appUser?.settings?.workflowsView ?? 'cards');

    const changeViewMode = async (mode: ViewMode) =>
    {
        setViewMode(mode);
        if (!appUser?.settings) return;
        const updated = {...appUser.settings, workflowsView: mode};
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
