import {useEffect, useState} from "react";
import {AppUserPublicDto} from "../../../../models/models.tsx";
import {
    fetchMyOrganizationGroups,
    fetchMyOrganizationUsers,
    OrganizationGroupBasicDto
} from "../../../../../services/organizationApi";

export const useMyOrganizationDirectory = () =>
{
    const [isLoadingUsers, setIsLoadingUsers] = useState<boolean>(false);
    const [isLoadingGroups, setIsLoadingGroups] = useState<boolean>(false);
    const [myOrgUsers, setMyOrgUsers] = useState<AppUserPublicDto[]>([]);
    const [myOrgGroups, setMyOrgGroups] = useState<OrganizationGroupBasicDto[]>([]);

    useEffect(() =>
    {
        const loadMyOrganizationUsers = async () =>
        {
            setIsLoadingUsers(true);
            try
            {
                const users = await fetchMyOrganizationUsers();
                setMyOrgUsers(users);
            }
            catch (error)
            {
                console.error("Error loading my organization users:", error);
            }
            finally
            {
                setIsLoadingUsers(false);
            }
        };

        const loadMyOrganizationGroups = async () =>
        {
            setIsLoadingGroups(true);
            try
            {
                const groups: OrganizationGroupBasicDto[] = await fetchMyOrganizationGroups();
                setMyOrgGroups(groups);
            }
            catch (error)
            {
                console.error("Error loading my organization groups:", error);
            }
            finally
            {
                setIsLoadingGroups(false);
            }
        };

        loadMyOrganizationUsers();
        loadMyOrganizationGroups();
    }, []);

    return {isLoadingUsers, isLoadingGroups, myOrgUsers, myOrgGroups};
};
