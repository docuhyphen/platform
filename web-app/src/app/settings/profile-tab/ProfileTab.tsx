import {useState} from "react";
import {SelectTabData, SelectTabEvent, Tab, TabList, TabValue} from "@fluentui/react-components";
import {useProfileTabStyles} from "./ProfileTabStyles.tsx";
import {useAuth} from "../../../context/AuthContext.tsx";
import {AppUserDetailedDto, AppUserSettingsDto} from "../../models/models.tsx";
import {updateAppUserSettings} from "../../../services/appUserApi.ts";
import {formatDate} from "../../helpers.ts";
import BasicDetailsEditDialog from "./basic-details-edit-dialog/BasicDetailsEditDialog.tsx";
import PhoneManagementDialog, {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";
import AppUserEmailUpdateDialog from "../../components/app-user-email-update-dialog/AppUserEmailUpdateDialog.tsx";
import PasswordResetDialog from "./password-reset-dialog/PasswordResetDialog.tsx";
import ProfileOverviewCard from "./profile-overview-card/ProfileOverviewCard.tsx";
import ProfilePictureDialog from "./profile-picture-dialog/ProfilePictureDialog.tsx";
import ProfileSecurityCard from "./profile-security-card/ProfileSecurityCard.tsx";
import ProfileNotificationsCard from "./profile-notifications-card/ProfileNotificationsCard.tsx";
import ProfileSecurityEvents from "./profile-security-events/ProfileSecurityEvents.tsx";
import MfaSettingsDialog from "./mfa-settings-dialog/MfaSettingsDialog.tsx";

type ProfileSubTab = "details" | "security";

const ProfileTab = () =>
{
    const {appUser, token, setAppUser} = useAuth();
    const styles = useProfileTabStyles();
    const [subTab, setSubTab] = useState<TabValue>("details" satisfies ProfileSubTab);
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);
    const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [isContactDetailsEditDialogOpen, setIsContactDetailsEditDialogOpen] = useState(false);
    const [isEmailUpdateDialogOpen, setIsEmailUpdateDialogOpen] = useState(false);
    const [isMfaDialogOpen, setIsMfaDialogOpen] = useState(false);
    const [isProfilePictureDialogOpen, setIsProfilePictureDialogOpen] = useState(false);

    const notifyLoginChange = async (_, data) =>
    {
        try
        {
            if (!appUser)
            {
                return;
            }

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
        catch (error)
        {
            console.error("Failed to update settings:", error);
        }
    };

    const onAddOrEditPhone = () =>
    {
        setPhoneManagementMode(
            appUser?.person.contactDetails?.phoneNumber
                ? PhoneManagementMode.EDIT
                : PhoneManagementMode.ADD
        );
        setIsContactDetailsEditDialogOpen(true);
    };

    const memberSince = appUser?.createdDate ? formatDate(appUser.createdDate) : "Not available";

    const onTabSelect = (_: SelectTabEvent, data: SelectTabData) =>
    {
        setSubTab(data.value);
    };

    return <>
        <div id={"profile-tab-container"} className={styles.tabsContainer}>
            <div className={styles.tabListWrapper}>
                <TabList
                    id={"profile-tab-subtab-list"}
                    selectedValue={subTab}
                    onTabSelect={onTabSelect}
                    size="medium"
                >
                    <Tab id={"profile-tab-details-subtab"} value={"details" satisfies ProfileSubTab}>
                        Details
                    </Tab>
                    <Tab id={"profile-tab-security-subtab"} value={"security" satisfies ProfileSubTab}>
                        Security Activity
                    </Tab>
                </TabList>
            </div>

            <div className={styles.tabContent}>
                {subTab === "details" && (
                    <div id={"profile-tab-details-content"} className={styles.detailsScrollableContent}>
                        <ProfileOverviewCard
                            appUser={appUser}
                            memberSince={memberSince}
                            onEditProfile={() => setIsBasicDetailsDialogOpen(true)}
                            onEditPhone={onAddOrEditPhone}
                            onEditEmail={() => setIsEmailUpdateDialogOpen(true)}
                            onEditAvatar={() => setIsProfilePictureDialogOpen(true)}
                        />


                        <div id={"profile-tab-card-grid"} className={styles.cardGrid}>
                            <ProfileSecurityCard
                                onChangePassword={() => setIsPasswordResetDialogOpen(true)}
                                mfaMethod={appUser?.mfaMethod || 'EMAIL'}
                                emailFallbackEnabled={appUser?.emailMfaFallbackEnabled || false}
                                onConfigureMfa={() => setIsMfaDialogOpen(true)}
                            />

                            <ProfileNotificationsCard
                                appUser={appUser}
                                onNotifyLoginChange={notifyLoginChange}
                            />
                        </div>
                    </div>
                )}

                {subTab === "security" && (
                    <div id={"profile-tab-security-content"} className={styles.securityTabContent}>
                        <ProfileSecurityEvents/>
                    </div>
                )}
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
            onComplete={(contactDetails) =>
            {
                setAppUser({
                    ...appUser,
                    person: {
                        ...appUser?.person,
                        contactDetails: contactDetails
                    }
                } as AppUserDetailedDto);
            }}
        />

        <BasicDetailsEditDialog
            isOpen={isBasicDetailsDialogOpen}
            onDismiss={() => setIsBasicDetailsDialogOpen(false)}
        />

        <ProfilePictureDialog
            isOpen={isProfilePictureDialogOpen}
            onDismiss={() => setIsProfilePictureDialogOpen(false)}
        />

        <PasswordResetDialog
            isOpen={isPasswordResetDialogOpen}
            onDismiss={() => setIsPasswordResetDialogOpen(false)}
        />

        <MfaSettingsDialog
            open={isMfaDialogOpen}
            onOpenChange={setIsMfaDialogOpen}
            onUpdated={(configuration) =>
            {
                if (!appUser) return;
                setAppUser({
                    ...appUser,
                    mfaMethod: configuration.method,
                    emailMfaFallbackEnabled: configuration.emailFallbackEnabled,
                });
            }}
        />
    </>;
};

export default ProfileTab;
