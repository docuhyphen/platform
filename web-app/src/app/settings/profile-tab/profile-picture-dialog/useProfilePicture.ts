import {useCallback, useState} from "react";
import {useAuth} from "../../../../context/AuthContext.tsx";
import {AppUserDetailedDto} from "../../../models/models.tsx";
import {
    deleteAppUserAvatar,
    fetchAppUserAvatarObjectUrl,
    uploadAppUserAvatar
} from "../../../../services/appUserAvatarApi.ts";

const ALLOWED_EXTENSIONS = ["png", "jpg", "jpeg", "webp", "gif"];
const MAX_SIZE_BYTES = 5 * 1024 * 1024;

const getAvatarName = (appUser: AppUserDetailedDto | null): string =>
{
    const name = `${appUser?.person?.firstName ?? ""} ${appUser?.person?.lastName ?? ""}`.trim();
    return name || "Profile";
};

export interface ProfilePictureManager
{
    currentAvatarUrl: string | undefined;
    selectedPreviewUrl: string | null;
    hasSelection: boolean;
    hasExistingAvatar: boolean;
    avatarName: string;
    isBusy: boolean;
    error: string | null;
    selectFile: (file: File) => void;
    clearSelection: () => void;
    confirmUpload: () => Promise<boolean>;
    removeAvatar: () => Promise<boolean>;
    reset: () => void;
}

/**
 * Owns the state and side effects for previewing, replacing, and removing the signed in
 * user's profile picture. A newly chosen image is staged with a local preview URL so the
 * user can review it in a circular preview before committing the upload.
 */
export const useProfilePicture = (): ProfilePictureManager =>
{
    const {appUser, token, setAppUser} = useAuth();
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [selectedFile, setSelectedFile] = useState<File | null>(null);
    const [selectedPreviewUrl, setSelectedPreviewUrl] = useState<string | null>(null);

    const currentAvatarUrl = appUser?.avatarUrl && appUser.avatarUrl.startsWith("blob:")
        ? appUser.avatarUrl
        : undefined;

    const clearSelection = useCallback(() =>
    {
        setSelectedPreviewUrl(prev =>
        {
            if (prev)
            {
                URL.revokeObjectURL(prev);
            }
            return null;
        });
        setSelectedFile(null);
    }, []);

    const reset = useCallback(() =>
    {
        clearSelection();
        setError(null);
    }, [clearSelection]);

    const selectFile = useCallback((file: File) =>
    {
        const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
        if (!ALLOWED_EXTENSIONS.includes(extension))
        {
            setError(`Unsupported image type. Allowed types: ${ALLOWED_EXTENSIONS.join(", ")}`);
            return;
        }
        if (file.size > MAX_SIZE_BYTES)
        {
            setError("Image is too large. Maximum size is 5 MB");
            return;
        }

        setError(null);
        setSelectedPreviewUrl(prev =>
        {
            if (prev)
            {
                URL.revokeObjectURL(prev);
            }
            return URL.createObjectURL(file);
        });
        setSelectedFile(file);
    }, []);

    const applyAvatarUrl = useCallback((nextUrl: string | null) =>
    {
        if (!appUser)
        {
            return;
        }
        if (currentAvatarUrl)
        {
            URL.revokeObjectURL(currentAvatarUrl);
        }
        setAppUser({...appUser, avatarUrl: nextUrl});
    }, [appUser, currentAvatarUrl, setAppUser]);

    const confirmUpload = useCallback(async (): Promise<boolean> =>
    {
        if (!selectedFile)
        {
            return false;
        }

        try
        {
            setError(null);
            setIsBusy(true);
            await uploadAppUserAvatar(selectedFile, token);
            const objectUrl = await fetchAppUserAvatarObjectUrl(token);
            applyAvatarUrl(objectUrl);
            clearSelection();
            return true;
        }
        catch
        {
            setError("Could not update your profile picture. Please try again.");
            return false;
        }
        finally
        {
            setIsBusy(false);
        }
    }, [applyAvatarUrl, clearSelection, selectedFile, token]);

    const removeAvatar = useCallback(async (): Promise<boolean> =>
    {
        try
        {
            setError(null);
            setIsBusy(true);
            await deleteAppUserAvatar(token);
            applyAvatarUrl(null);
            clearSelection();
            return true;
        }
        catch
        {
            setError("Could not remove your profile picture. Please try again.");
            return false;
        }
        finally
        {
            setIsBusy(false);
        }
    }, [applyAvatarUrl, clearSelection, token]);

    return {
        currentAvatarUrl,
        selectedPreviewUrl,
        hasSelection: selectedFile !== null,
        hasExistingAvatar: currentAvatarUrl !== undefined,
        avatarName: getAvatarName(appUser),
        isBusy,
        error,
        selectFile,
        clearSelection,
        confirmUpload,
        removeAvatar,
        reset,
    };
};

