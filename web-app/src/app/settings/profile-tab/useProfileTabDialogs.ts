import {useState} from "react";
import {PhoneManagementMode} from "../../components/phone-management/PhoneManagementDialog.tsx";

export const useProfileTabDialogs = () =>
{
    const [isBasicDetailsDialogOpen, setIsBasicDetailsDialogOpen] = useState(false);
    const [isPasswordResetDialogOpen, setIsPasswordResetDialogOpen] = useState(false);
    const [phoneManagementMode, setPhoneManagementMode] = useState(PhoneManagementMode.ADD);
    const [isContactDetailsEditDialogOpen, setIsContactDetailsEditDialogOpen] = useState(false);
    const [isEmailUpdateDialogOpen, setIsEmailUpdateDialogOpen] = useState(false);
    const [isMfaDialogOpen, setIsMfaDialogOpen] = useState(false);
    const [isProfilePictureDialogOpen, setIsProfilePictureDialogOpen] = useState(false);

    const openPhoneManagement = (hasPhoneNumber: boolean) =>
    {
        setPhoneManagementMode(hasPhoneNumber ? PhoneManagementMode.EDIT : PhoneManagementMode.ADD);
        setIsContactDetailsEditDialogOpen(true);
    };

    return {
        isBasicDetailsDialogOpen,
        setIsBasicDetailsDialogOpen,
        isPasswordResetDialogOpen,
        setIsPasswordResetDialogOpen,
        phoneManagementMode,
        isContactDetailsEditDialogOpen,
        setIsContactDetailsEditDialogOpen,
        isEmailUpdateDialogOpen,
        setIsEmailUpdateDialogOpen,
        isMfaDialogOpen,
        setIsMfaDialogOpen,
        isProfilePictureDialogOpen,
        setIsProfilePictureDialogOpen,
        openPhoneManagement,
    };
};

export type ProfileTabDialogsState = ReturnType<typeof useProfileTabDialogs>;
