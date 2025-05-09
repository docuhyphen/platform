import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Input,
    Spinner
} from "@fluentui/react-components";
import React, {useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import apiClient from "../../../../services/apiClient";
import {AppUserDetailedDto} from "../../../models/models.tsx";

interface BasicDetailsEditDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
}

const BasicDetailsEditDialog: React.FC<BasicDetailsEditDialogProps> = (
    {
        isOpen,
        onDismiss
    }
) =>
{
    const {appUser, token, setAppUser} = useAuth()
    const [firstName, setFirstName] = useState(appUser?.person.firstName);
    const [lastName, setLastName] = useState(appUser?.person.lastName);
    const [updatingProfile, setUpdatingProfile] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const onUpdate = async () =>
    {
        if (updatingProfile)
        {
            return
        }

        if (firstName?.length === 0 || lastName?.length === 0)
        {
            alert("First name and last name are required")
            return;
        }

        setUpdatingProfile(true)
        setError(null);

        try
        {
            const updatedPersonRequest = {
                firstName,
                lastName
            }

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
            } as AppUserDetailedDto

            setAppUser({
                ...updatedAppUser,
                person: {...updatedAppUser?.person, firstName, lastName}
            });

            onClose();
        }
        catch (e: any)
        {
            console.error("Failed to update profile:", e);
            setError(e.response?.data?.message || "Failed to update profile");
        }
        finally
        {
            setUpdatingProfile(false)
        }
    }

    const onClose = () =>
    {
        setFirstName(appUser?.person.firstName || '')
        setLastName(appUser?.person.lastName || '')
        setError(null);
        onDismiss()
    }

    return <Dialog modalType="alert"
                   open={isOpen}>
        <DialogSurface>
            <DialogBody>
                <DialogTitle>Update your profile</DialogTitle>
                <DialogContent>
                    {error && <div style={{color: 'red', marginBottom: '10px'}}>{error}</div>}

                    <Field label={"First Name"}>
                        <Input type={"text"}
                               value={firstName}
                               onChange={(e) => setFirstName(e.target.value)}/>
                    </Field>

                    <Field label={"Last Name"}>
                        <Input type={"text"}
                               value={lastName}
                               onChange={(e) => setLastName(e.target.value)}/>
                    </Field>
                </DialogContent>
            </DialogBody>
            <DialogActions>
                <Button appearance="primary"
                        shape={"circular"}
                        disabled={
                            updatingProfile ||
                            (firstName === appUser?.person.firstName && lastName === appUser?.person.lastName)
                        }
                        onClick={onUpdate}>
                    {updatingProfile && <Spinner size={"tiny"}/>}
                    Update
                </Button>
                <DialogTrigger disableButtonEnhancement>
                    <Button appearance="secondary"
                            shape={"circular"}
                            disabled={updatingProfile}
                            onClick={onClose}>
                        Close
                    </Button>
                </DialogTrigger>
            </DialogActions>
        </DialogSurface>
    </Dialog>
}

export default BasicDetailsEditDialog;