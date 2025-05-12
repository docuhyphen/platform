import {Button, Divider, Switch, Text} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import React, {useEffect, useState} from "react";
import {AppUserDetailedDto, AppUserSettingsDto} from "../../models/models.tsx";
import {updateAppUserSettings} from "../../../services/appUserApi.ts";
import BasicDetailsEditDialog from "./basic-details-edit-dialog/BasicDetailsEditDialog.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import AppUserEmailUpdateDialog from "../../components/app-user-email-update-dialog/AppUserEmailUpdateDialog.tsx";
import AllDeviceSignOutDialog from "./all-device-sign-out-dialog/AllDeviceSignOutDialog.tsx";

const ProfileTab = () =>
{
    const {appUser, token, setAppUser} = useAuth()
    const styles = useProfileTabStyles()
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);
    const [isAllDeviceSignOutDialogOpen, setIsAllDeviceSignOutDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [isContactDetailsEditDialogOpen, setIsContactDetailsEditDialogOpen] = useState(false);
    const [isEmailUpdateDialogOpen, setIsEmailUpdateDialogOpen] = useState(false);

    useEffect(() =>
    {
        console.log("ProfileTab mounted");
        console.log(appUser)
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
                    label="Send me by email every time I sign in"
                />
            }

            <Divider alignContent={"start"}
                     appearance={"brand"}>
                Basic Details
                <Button icon={<ProfileEditBasicDetailsIcon/>}
                        onClick={ () => setIsBasicDetailsDialogOpen(true)}
                        appearance={"subtle"}/>
            </Divider>
            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    First Name
                </Text>
                <Text size={500}>
                    {appUser?.person?.firstName}
                </Text>
            </div>
            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Last Name
                </Text>
                <Text size={500}>
                    {appUser?.person?.lastName}
                </Text>
            </div>

            <Divider alignContent={"start"}
                     appearance={"brand"}>
                Contact Details
            </Divider>

            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Email
                </Text>
                <Text size={500}>
                    <Button appearance={"subtle"}
                            size={"small"}
                            icon={<ProfileEditBasicDetailsIcon/>}
                            onClick={() => setIsEmailUpdateDialogOpen(true)}/>
                    {appUser?.email}
                </Text>
            </div>
            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Phone number
                </Text>
                <Text size={500}>
                    {appUser?.person?.contactDetails?.phoneNumber ? (
                        <>
                            <Button appearance={"subtle"}
                                    size={"small"}
                                    icon={<ProfileEditBasicDetailsIcon/>}
                                    onClick={onAddOrEditPhone}/>
                            {appUser.person.contactDetails.phoneNumber}
                        </>
                    ) : (
                        <Button appearance={"outline"}
                                shape={"circular"}
                                size={"small"}
                                onClick={onAddOrEditPhone}>
                            Add Phone Number
                        </Button>
                    )}
                </Text>
            </div>
            <Divider alignContent={"start"}
                     appearance={"brand"}>
                Security
            </Divider>
            <div>
                <Button appearance={"outline"}
                        shape={"circular"}
                        size={"medium"}> Change password</Button>
            </div>
            <div>
                <Button appearance={"outline"}
                        shape={"circular"}
                        onClick={() => setIsAllDeviceSignOutDialogOpen(true)}
                        size={"medium"}> Sign out of all devices</Button>
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

        <AllDeviceSignOutDialog
            isOpen={isAllDeviceSignOutDialogOpen}
            onDismiss={() => setIsAllDeviceSignOutDialogOpen(false)}
        />
    </>
}

export default ProfileTab;