import {Button, Divider, Switch, Text} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {ProfileEditBasicDetailsIcon} from "../../components/IconBundles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import React, {useEffect, useState} from "react";
import {AppUserSettingsDto} from "../../models/models.tsx";
import {updateAppUserSettings} from "../../../services/appUserApi.ts";
import BasicDetailsEditDialog from "./basic-details-edit-dialog/BasicDetailsEditDialog.tsx";

const ProfileTab = () =>
{
    const {appUser, token, setAppUser} = useAuth()
    const styles = useProfileTabStyles()
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);

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
                <Button icon={<ProfileEditBasicDetailsIcon/>}
                        appearance={"subtle"}/>
            </Divider>

            <div className={styles.dataContainer}>
                <Text size={500}
                      italic={true}
                      className={styles.dataName}>
                    Email
                </Text>
                <Text size={500}>
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
                        appUser.person.contactDetails.phoneNumber
                    ) : (
                        <Button appearance={"outline"}
                                shape={"circular"}
                                size={"small"}>
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
                        size={"medium"}> Sign out of all devices</Button>
            </div>
        </div>
        <BasicDetailsEditDialog
            isOpen={isBasicDetailsDialogOpen}
            onDismiss={() => setIsBasicDetailsDialogOpen(false)}
        />
    </>
}

export default ProfileTab;