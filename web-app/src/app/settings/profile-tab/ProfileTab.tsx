import {Button, Divider, Switch, Text} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {useEffect, useState} from "react";
import {AppUserDetailedDto, AppUserSettingsDto} from "../../models/models.tsx";
import {updateAppUserSettings} from "../../../services/appUserApi.ts";
import BasicDetailsEditDialog from "./basic-details-edit-dialog/BasicDetailsEditDialog.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import AppUserEmailUpdateDialog from "../../components/app-user-email-update-dialog/AppUserEmailUpdateDialog.tsx";
import PasswordResetDialog from "./password-reset-dialog/PasswordResetDialog.tsx";
import {PasswordRegular, PhoneDismissRegular} from "@fluentui/react-icons";

const ProfileTab = () =>
{
    const {appUser, token, setAppUser} = useAuth()
    const styles = useProfileTabStyles()
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);
    const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [isContactDetailsEditDialogOpen, setIsContactDetailsEditDialogOpen] = useState(false);
    const [isEmailUpdateDialogOpen, setIsEmailUpdateDialogOpen] = useState(false);

    useEffect(() =>
    {
    }, [appUser]);

    const notifyLoginChange = async (e, data) =>
    {
        try
        {
            if (!appUser) return;

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
        catch (e)
        {
            console.error("Failed to update settings:", e);
        }
    }

    const onAddOrEditPhone = () =>
    {
        setPhoneManagementMode(PhoneManagementMode.ADD)

        if (appUser?.person.contactDetails?.phoneNumber)
        {
            setPhoneManagementMode(PhoneManagementMode.EDIT)
        }

        setIsContactDetailsEditDialogOpen(true)
    }

    return <>
        <div className={styles.container}>

            {appUser &&
                <Switch
                    checked={appUser.settings.notifyLogin}
                    onChange={notifyLoginChange}
                    label="Send me an email every time I sign in"
                />
            }

            <Divider alignContent={"start"}
                     appearance={"brand"}
                     className={styles.mainDivider}>
                Basic Details
                <Button id={"button-edit-basic-details"}
                        icon={<ProfileEditBasicDetailsIcon/>}
                        onClick={() => setIsBasicDetailsDialogOpen(true)}
                        shape={"circular"}
                        appearance={"subtle"}/>
            </Divider>
            <div className={styles.dataContainer}>
                <Text size={500}>
                    {appUser?.person?.firstName} {appUser?.person?.lastName}
                </Text>
            </div>

            <Divider alignContent={"start"}
                     appearance={"brand"}
                     className={styles.mainDivider}>
                Contact Details
            </Divider>

            <div className={styles.dataContainer}>
                <Text size={500} className={styles.dataEditable}>
                    <Button id={"button-edit-email"}
                            appearance={"subtle"}
                            size={"small"}
                            shape={"circular"}
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={() => setIsEmailUpdateDialogOpen(true)}/>
                    {appUser?.email}
                </Text>
            </div>
            <div className={styles.dataContainer}>
                <Text size={500} className={styles.dataEditable}>
                    {appUser?.person?.contactDetails?.phoneNumber ? (
                        <>
                            <Button id={"button-edit-phone"}
                                    appearance={"subtle"}
                                    size={"small"}
                                    shape={"circular"}
                                    icon={<ProfileEditBasicDetailsIcon/>}
                                    onClick={onAddOrEditPhone}/>
                            {appUser.person.contactDetails.phoneNumber}
                        </>
                    ) : (
                        <Button id={"button-add-phone-number"}
                                appearance={"secondary"}
                                shape={"circular"}
                                icon={<PhoneDismissRegular></PhoneDismissRegular>}
                                onClick={onAddOrEditPhone}>
                            Add phone number
                        </Button>
                    )}
                </Text>
            </div>
            <Divider alignContent={"start"}
                     appearance={"brand"}
                     className={styles.mainDivider}>
                Security
            </Divider>
            <div>
                <Button id={"button-change-password"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<PasswordRegular/>}
                        onClick={() => setIsPasswordResetDialogOpen(true)}>
                    Change password
                </Button>
            </div>
        </div>

        <AppUserEmailUpdateDialog
            isOpen={isEmailUpdateDialogOpen}
            onDismiss={() => setIsEmailUpdateDialogOpen(false)}
            currentEmail={appUser?.email}
        />

        <PhoneManagementDialog
            isOpen={isContactDetailsEditDialogOpen}
            mode={phoneManagementMode}
            onDismiss={() => setIsContactDetailsEditDialogOpen(false)}
            contactDetails={appUser?.person.contactDetails}
            onComplete={
                (contactDetails) =>
                {
                    setAppUser({
                        ...appUser,
                        person: {
                            ...appUser?.person,
                            contactDetails: contactDetails
                        }
                    } as AppUserDetailedDto)
                }
            }/>

        <BasicDetailsEditDialog
            isOpen={isBasicDetailsDialogOpen}
            onDismiss={() => setIsBasicDetailsDialogOpen(false)}
        />

        <PasswordResetDialog
            isOpen={isPasswordResetDialogOpen}
            onDismiss={() => setIsPasswordResetDialogOpen(false)}/>
    </>
}

export default ProfileTab;