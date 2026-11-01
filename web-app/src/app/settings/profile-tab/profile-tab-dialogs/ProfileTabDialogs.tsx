import {useAuth} from "../../../../context/AuthContext.tsx";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import BasicDetailsEditDialog from "../basic-details-edit-dialog/BasicDetailsEditDialog.tsx";
import PhoneManagementDialog from "../../../components/phone-management/PhoneManagementDialog.tsx";
import AppUserEmailUpdateDialog from "../../../components/app-user-email-update-dialog/AppUserEmailUpdateDialog.tsx";
import PasswordResetDialog from "../password-reset-dialog/PasswordResetDialog.tsx";
import ProfilePictureDialog from "../profile-picture-dialog/ProfilePictureDialog.tsx";
import MfaSettingsDialog from "../mfa-settings-dialog/MfaSettingsDialog.tsx";
import {ProfileTabDialogsState} from "../useProfileTabDialogs.ts";

interface ProfileTabDialogsProps
{
    dialogs: ProfileTabDialogsState;
}

const ProfileTabDialogs = ({dialogs}: ProfileTabDialogsProps) =>
{
    const {appUser, setAppUser} = useAuth();

    return (
        <>
            <AppUserEmailUpdateDialog
                isOpen={dialogs.isEmailUpdateDialogOpen}
                onDismiss={() => dialogs.setIsEmailUpdateDialogOpen(false)}
                currentEmail={appUser?.email}
            />

            <PhoneManagementDialog
                isOpen={dialogs.isContactDetailsEditDialogOpen}
                mode={dialogs.phoneManagementMode}
                onDismiss={() => dialogs.setIsContactDetailsEditDialogOpen(false)}
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
                isOpen={dialogs.isBasicDetailsDialogOpen}
                onDismiss={() => dialogs.setIsBasicDetailsDialogOpen(false)}
            />

            <ProfilePictureDialog
                isOpen={dialogs.isProfilePictureDialogOpen}
                onDismiss={() => dialogs.setIsProfilePictureDialogOpen(false)}
            />

            <PasswordResetDialog
                isOpen={dialogs.isPasswordResetDialogOpen}
                onDismiss={() => dialogs.setIsPasswordResetDialogOpen(false)}
            />

            <MfaSettingsDialog
                open={dialogs.isMfaDialogOpen}
                onOpenChange={dialogs.setIsMfaDialogOpen}
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
        </>
    );
};

export default ProfileTabDialogs;
