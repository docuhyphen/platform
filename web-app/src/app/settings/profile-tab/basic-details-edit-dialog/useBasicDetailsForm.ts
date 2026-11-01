import {useState} from "react";
import axios from "axios";
import {useAuth} from "../../../../context/AuthContext.tsx";
import apiClient from "../../../../services/apiClient";
import {AppUserDetailedDto} from "../../../models/models.tsx";

export const useBasicDetailsForm = (onDismiss: () => void) =>
{
    const {appUser, token, setAppUser} = useAuth();
    const [firstName, setFirstName] = useState(appUser?.person.firstName);
    const [lastName, setLastName] = useState(appUser?.person.lastName);
    const [updatingProfile, setUpdatingProfile] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const onClose = () =>
    {
        setFirstName(appUser?.person.firstName || '');
        setLastName(appUser?.person.lastName || '');
        setError(null);
        onDismiss();
    };

    const onUpdate = async () =>
    {
        if (updatingProfile)
        {
            return;
        }

        if (!firstName?.trim() || !lastName?.trim())
        {
            setError("First name and last name are required");
            return;
        }

        setUpdatingProfile(true);
        setError(null);

        try
        {
            const updatedPersonRequest = {
                firstName,
                lastName
            };

            await apiClient.put('/app-user/person', updatedPersonRequest, {
                headers: {
                    Authorization: `Bearer ${token}`
                }
            });

            const updatedAppUser = {
                ...appUser,
                person: {
                    ...appUser?.person,
                    firstName,
                    lastName
                }
            } as AppUserDetailedDto;

            setAppUser({
                ...updatedAppUser,
                person: {...updatedAppUser?.person, firstName, lastName}
            });

            onClose();
        }
        catch (e: unknown)
        {
            console.error("Failed to update profile:", e);
            const failure = axios.isAxiosError<{message?: string; errorMessage?: string}>(e) ? e : undefined;
            const serverMsg = failure?.response?.data?.message || failure?.response?.data?.errorMessage;
            if (serverMsg)
            {
                setError(serverMsg);
            }
            else if ((failure?.response?.status ?? 0) >= 500 || !failure?.response)
            {
                setError("Something went wrong on our side. Please try again in a moment.");
            }
            else
            {
                setError("Failed to update profile");
            }
        }
        finally
        {
            setUpdatingProfile(false);
        }
    };

    const isUnchanged = firstName === appUser?.person.firstName && lastName === appUser?.person.lastName;

    return {firstName, setFirstName, lastName, setLastName, updatingProfile, error, isUnchanged, onUpdate, onClose};
};
