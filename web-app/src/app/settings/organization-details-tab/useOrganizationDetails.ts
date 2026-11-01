import {useCallback, useEffect, useState} from "react";
import {AxiosError} from "axios";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {updateOrganizationSettings} from "../../../services/organizationApi";
import {ContactDetailsDetailedDto, OrganizationDetailedDto, OrganizationSettingsDto} from "../../models/models.tsx";

export const useOrganizationDetails = () =>
{
    const {appUser, token} = useAuth();
    const [organization, setOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [savingSettings, setSavingSettings] = useState(false);
    const [organizationSettings, setOrganizationSettings] = useState<OrganizationSettingsDto | null>(null);

    const getOrganization = useCallback(async () =>
    {
        if (!appUser?.id || !appUser?.person?.id) return;

        setFetchingOrganization(true);
        setError(null);

        try
        {
            const fetched = await fetchAppUserPersonOrganization(appUser.id, appUser.person.id, token || undefined);
            setOrganization(fetched);
            setOrganizationSettings(fetched.settings);
        }
        catch (err: unknown)
        {
            if (!(err instanceof AxiosError))
            {
                const message = err instanceof Error ? err.message : "Failed to fetch organization";
                setError(message);
                console.error("Failed to fetch organization:", err);
            }
        }
        finally
        {
            setFetchingOrganization(false);
        }
    }, [appUser?.id, appUser?.person?.id, token]);

    const handleSettingChange = async (setting: keyof OrganizationSettingsDto, value: boolean) =>
    {
        if (!organization?.id || !organizationSettings) return;

        setSavingSettings(true);
        setError(null);

        const updatedSettings = {
            ...organizationSettings,
            [setting]: value
        };

        try
        {
            await updateOrganizationSettings(
                organization.id,
                updatedSettings,
                token || undefined
            );
            setOrganizationSettings(updatedSettings);
            setOrganization({
                ...organization,
                settings: updatedSettings
            });
        }
        catch (err: unknown)
        {
            setError(err instanceof Error && err.message ? err.message : "Failed to update organization settings");
            console.error("Failed to update organization settings:", err);
        }
        finally
        {
            setSavingSettings(false);
        }
    };

    const handleContactDetailsUpdate = (updatedContactDetails: ContactDetailsDetailedDto) =>
    {
        if (!organization) return;

        setOrganization({
            ...organization,
            contactDetails: updatedContactDetails
        });
    };

    useEffect(() =>
    {
        getOrganization()
    }, [getOrganization]);

    return {
        organization,
        setOrganization,
        fetchingOrganization,
        error,
        savingSettings,
        organizationSettings,
        handleSettingChange,
        handleContactDetailsUpdate,
    };
};
