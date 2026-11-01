import {useEffect, useState} from "react";
import {AxiosError} from "axios";
import {useAuth} from "../../../context/AuthContext";
import {fetchAppUserPersonOrganization} from "../../../services/appUserApi";
import {OrganizationDetailedDto} from "../../models/models.tsx";

export const useOrganization = () =>
{
    const {appUser, token} = useAuth();
    const [organization, setOrganization] = useState<OrganizationDetailedDto | null>(null);
    const [fetchingOrganization, setFetchingOrganization] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const getOrganization = async () =>
    {
        if (!appUser?.id || !appUser?.person?.id) return;

        setFetchingOrganization(true);
        setError(null);

        try
        {
            setOrganization(await fetchAppUserPersonOrganization(appUser.id, appUser.person.id, token || undefined));
        }
        catch (err: unknown)
        {
            if (!(err instanceof AxiosError))
            {
                setError(err instanceof Error && err.message ? err.message : "Failed to fetch organization");
                console.error("Failed to fetch organization:", err);
            }
        }
        finally
        {
            setFetchingOrganization(false);
        }
    };

    useEffect(() =>
    {
        getOrganization();
    }, []);

    return {organization, fetchingOrganization, error, getOrganization};
};
