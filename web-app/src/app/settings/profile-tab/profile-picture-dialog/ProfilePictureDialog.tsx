import {ChangeEvent, useRef} from "react";
import {
    Avatar,
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    Spinner,
    Text
} from "@fluentui/react-components";
import {ArrowUploadRegular, PersonCircleRegular} from "@fluentui/react-icons";
import {useProfilePicture} from "./useProfilePicture.ts";
import ProfilePictureActions from "./ProfilePictureActions.tsx";
import {useProfilePictureDialogStyles} from "./ProfilePictureDialogStyles.tsx";

interface ProfilePictureDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
}

const ProfilePictureDialog = (
    {
        isOpen,
        onDismiss
    }: ProfilePictureDialogProps
) =>
{
    const styles = useProfilePictureDialogStyles();
    const picture = useProfilePicture();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const previewSrc = picture.selectedPreviewUrl ?? picture.currentAvatarUrl;

    const onSelectFile = () => fileInputRef.current?.click();

    const onFileChange = (event: ChangeEvent<HTMLInputElement>) =>
    {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (file)
        {
            picture.selectFile(file);
        }
    };

    const handleClose = () =>
    {
        picture.reset();
        onDismiss();
    };

    const onUpload = async () =>
    {
        const uploaded = await picture.confirmUpload();
        if (uploaded)
        {
            onDismiss();
        }
    };

    return <Dialog
        modalType={"modal"}
        open={isOpen}
        onOpenChange={(_, data) =>
        {
            if (!data.open)
            {
                handleClose();
            }
        }}
    >
        <DialogSurface id={"profile-picture-dialog-surface"}>
            <DialogBody id={"profile-picture-dialog-dialog-body-1"}>
                <DialogTitle id={"profile-picture-dialog-title"}>Profile picture</DialogTitle>
                <DialogContent id={"profile-picture-dialog-dialog-content-1"}
                               className={styles.content}>
                    <Avatar
                        id={"profile-picture-dialog-preview"}
                        name={picture.avatarName}
                        size={128}
                        icon={<PersonCircleRegular/>}
                        image={{src: previewSrc}}
                    />

                    <Text
                        id={"profile-picture-dialog-helper"}
                        size={200}
                        className={styles.helperText}
                    >
                        {picture.hasSelection
                            ? "Preview how your new picture will look, then upload to save it."
                            : "Choose a PNG, JPG, WEBP or GIF image up to 5 MB."}
                    </Text>

                    <ProfilePictureActions hasSelection={picture.hasSelection}
                                           hasExistingAvatar={picture.hasExistingAvatar}
                                           isBusy={picture.isBusy}
                                           onSelectFile={onSelectFile}
                                           onClearSelection={picture.clearSelection}
                                           onRemoveAvatar={picture.removeAvatar}/>

                    {picture.error && <Text
                        id={"profile-picture-dialog-error"}
                        size={200}
                        className={styles.errorText}
                    >
                        {picture.error}
                    </Text>}

                    <input
                        id={"profile-picture-dialog-file-input"}
                        ref={fileInputRef}
                        className={styles.hiddenInput}
                        type={"file"}
                        accept={"image/png,image/jpeg,image/webp,image/gif"}
                        onChange={onFileChange}
                    />
                </DialogContent>
            </DialogBody>
            <DialogActions id={"profile-picture-dialog-dialog-actions-1"}>
                <Button
                    id={"button-upload-profile-picture"}
                    appearance={"primary"}
                    shape={"circular"}
                    icon={picture.isBusy ? <Spinner id={"profile-picture-dialog-spinner-1"}
                                                    size={"tiny"}/> : <ArrowUploadRegular/>}
                    disabled={picture.isBusy || !picture.hasSelection}
                    onClick={onUpload}
                >
                    Upload
                </Button>
                <Button
                    id={"button-cancel-profile-picture"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={picture.isBusy}
                    onClick={handleClose}
                >
                    Cancel
                </Button>
            </DialogActions>
        </DialogSurface>
    </Dialog>;
};

export default ProfilePictureDialog;
