import {useState} from "react";
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
import ProfileSecurityCard from "./profile-security-card/ProfileSecurityCard.tsx";
import ProfileNotificationsCard from "./profile-notifications-card/ProfileNotificationsCard.tsx";
import ProfileSecurityEvents from "./profile-security-events/ProfileSecurityEvents.tsx";
import MfaSettingsDialog from "./mfa-settings-dialog/MfaSettingsDialog.tsx";

const ProfileTab = () =>
{
    const {appUser, token, setAppUser} = useAuth();
    const styles = useProfileTabStyles();
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);
    const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [isContactDetailsEditDialogOpen, setIsContactDetailsEditDialogOpen] = useState(false);
    const [isEmailUpdateDialogOpen, setIsEmailUpdateDialogOpen] = useState(false);
    const [isMfaDialogOpen, setIsMfaDialogOpen] = useState(false);

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
    const linkedProvidersCount = appUser?.identityProviders?.length || 0;

    return <>
        <div id={"profile-tab-container"} className={styles.container}>
            <ProfileOverviewCard
                appUser={appUser}
                memberSince={memberSince}
                onEditProfile={() => setIsBasicDetailsDialogOpen(true)}
                onEditPhone={onAddOrEditPhone}
                onEditEmail={() => setIsEmailUpdateDialogOpen(true)}
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

            <ProfileSecurityEvents/>
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
