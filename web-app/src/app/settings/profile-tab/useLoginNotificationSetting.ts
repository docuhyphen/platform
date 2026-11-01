import {ChangeEvent} from "react";
import {SwitchOnChangeData} from "@fluentui/react-components";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserSettingsDto} from "../../models/models.tsx";
import {updateAppUserSettings} from "../../../services/appUserApi.ts";

export const useLoginNotificationSetting = () =>
{
    const {appUser, token, setAppUser} = useAuth();

    const notifyLoginChange = async (_: ChangeEvent<HTMLInputElement>, data: SwitchOnChangeData) =>
    {
        try
        {
            if (!appUser)
            {
                return;
            }

            const updatedSettings: AppUserSettingsDto = {
                ...appUser.settings,
                notifyLogin: data.checked
            };

            await updateAppUserSettings(updatedSettings, token);

            setAppUser({
                ...appUser,
                settings: updatedSettings
            });
        }
        catch (error)
        {
            console.error("Failed to update settings:", error);
        }
    };

    return {notifyLoginChange};
};
