import {useEffect, useState} from "react";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {fetchMyOrganizationUsers} from "../../../../../services/organizationApi";

export const useMyOrganizationUsers = (enabled: boolean) =>
{
    const [orgUsers, setOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [usersLoaded, setUsersLoaded] = useState<boolean>(false);

    useEffect(() =>
    {
        if (!enabled || usersLoaded)
        {
            return;
        }

        let cancelled = false;
        (async () =>
        {
            setIsLoadingUsers(true);
            try
            {
                const users = await fetchMyOrganizationUsers();
                if (!cancelled)
                {
                    setOrgUsers(users ?? []);
                    setUsersLoaded(true);
                }
            }
            catch (error)
            {
                console.error("Error loading my organization users:", error);
            }
            finally
            {
                if (!cancelled)
                {
                    setIsLoadingUsers(false);
                }
            }
        })();

        return () =>
        {
            cancelled = true;
        };
    }, [enabled, usersLoaded]);

    return {orgUsers, isLoadingUsers};
};
