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
import {useBasicDetailsEditDialogStyles} from "./BasicDetailsEditDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";

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
    const styles = useBasicDetailsEditDialogStyles()
    const globalStyles = useGlobalStyles()
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

        if (!firstName?.trim() || !lastName?.trim())
        {
            setError("First name and last name are required")
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
            const serverMsg = e?.response?.data?.message || e?.response?.data?.errorMessage;
            if (serverMsg)
            {
                setError(serverMsg);
            }
            else if (e?.response?.status >= 500 || !e?.response)
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
                <DialogContent className={styles.dialogContentContainer}>
                    <div className={styles.errorContainer}>{error || " "}</div>

                    <Field label={"Your first name"}>
                        <Input id={"input-first-name"}
                               type={"text"}
                               value={firstName}
                               maxLength={30}
                               onChange={(e) => setFirstName(e.target.value)}
                               onKeyDown={(e) => { if (e.key === "Enter") onUpdate(); }}/>
                    </Field>

                    <Field label={"Your last name"}>
                        <Input id={"input-last-name"}
                               type={"text"}
                               value={lastName}
                               maxLength={30}
                               onChange={(e) => setLastName(e.target.value)}
                               onKeyDown={(e) => { if (e.key === "Enter") onUpdate(); }}/>
                    </Field>
                </DialogContent>
            </DialogBody>
            <DialogActions>
                <Button id={"button-update-profile"}
                        appearance="primary"
                        shape={"circular"}
                        className={globalStyles.buttonWithLoading}
                        disabled={
                            updatingProfile ||
                            (firstName === appUser?.person.firstName && lastName === appUser?.person.lastName)
                        }
                        onClick={onUpdate}>
                    {updatingProfile && <Spinner size={"tiny"}/>}
                    {updatingProfile ? "Updating…" : "Update"}
                </Button>
                <DialogTrigger disableButtonEnhancement>
                    <Button id={"button-basic-details-close"}
                            appearance="secondary"
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