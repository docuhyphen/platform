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
import {ArrowUploadRegular, DeleteRegular, ImageRegular, PersonCircleRegular} from "@fluentui/react-icons";
import {useProfilePicture} from "./useProfilePicture.ts";
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
    const {
        currentAvatarUrl,
        selectedPreviewUrl,
        hasSelection,
        hasExistingAvatar,
        avatarName,
        isBusy,
        error,
        selectFile,
        clearSelection,
        confirmUpload,
        removeAvatar,
        reset
    } = useProfilePicture();
    const fileInputRef = useRef<HTMLInputElement>(null);

    const previewSrc = selectedPreviewUrl ?? currentAvatarUrl;

    const onSelectFile = () => fileInputRef.current?.click();

    const onFileChange = (event: ChangeEvent<HTMLInputElement>) =>
    {
        const file = event.target.files?.[0];
        event.target.value = "";
        if (file)
        {
            selectFile(file);
        }
    };

    const handleClose = () =>
    {
        reset();
        onDismiss();
    };

    const onUpload = async () =>
    {
        const uploaded = await confirmUpload();
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
            <DialogBody>
                <DialogTitle id={"profile-picture-dialog-title"}>Profile picture</DialogTitle>
                <DialogContent className={styles.content}>
                    <Avatar
                        id={"profile-picture-dialog-preview"}
                        name={avatarName}
                        size={128}
                        icon={<PersonCircleRegular/>}
                        image={{src: previewSrc}}
                    />

                    <Text
                        id={"profile-picture-dialog-helper"}
                        size={200}
                        className={styles.helperText}
                    >
                        {hasSelection
                            ? "Preview how your new picture will look, then upload to save it."
                            : "Choose a PNG, JPG, WEBP or GIF image up to 5 MB."}
                    </Text>

                    <div
                        id={"profile-picture-dialog-button-row"}
                        className={styles.buttonRow}
                    >
                        <Button
                            id={"button-choose-profile-picture"}
                            appearance={"secondary"}
                            shape={"circular"}
                            icon={<ImageRegular/>}
                            disabled={isBusy}
                            onClick={onSelectFile}
                        >
                            {hasExistingAvatar || hasSelection ? "Choose another" : "Choose photo"}
                        </Button>

                        {hasSelection && <Button
                            id={"button-clear-profile-picture-selection"}
                            appearance={"subtle"}
                            shape={"circular"}
                            disabled={isBusy}
                            onClick={clearSelection}
                        >
                            Discard selection
                        </Button>}

                        {!hasSelection && hasExistingAvatar && <Button
                            id={"button-remove-profile-picture"}
                            appearance={"subtle"}
                            shape={"circular"}
                            icon={<DeleteRegular/>}
                            disabled={isBusy}
                            onClick={removeAvatar}
                        >
                            Remove photo
                        </Button>}
                    </div>

                    {error && <Text
                        id={"profile-picture-dialog-error"}
                        size={200}
                        className={styles.errorText}
                    >
                        {error}
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
            <DialogActions>
                <Button
                    id={"button-upload-profile-picture"}
                    appearance={"primary"}
                    shape={"circular"}
                    icon={isBusy ? <Spinner size={"tiny"}/> : <ArrowUploadRegular/>}
                    disabled={isBusy || !hasSelection}
                    onClick={onUpload}
                >
                    Upload
                </Button>
                <Button
                    id={"button-cancel-profile-picture"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={isBusy}
                    onClick={handleClose}
                >
                    Cancel
                </Button>
            </DialogActions>
        </DialogSurface>
    </Dialog>;
};

export default ProfilePictureDialog;


