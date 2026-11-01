import {Button} from "@fluentui/react-components";
import {DeleteRegular, ImageRegular} from "@fluentui/react-icons";
import {useProfilePictureDialogStyles} from "./ProfilePictureDialogStyles.tsx";

interface ProfilePictureActionsProps
{
    hasSelection: boolean;
    hasExistingAvatar: boolean;
    isBusy: boolean;
    onSelectFile: () => void;
    onClearSelection: () => void;
    onRemoveAvatar: () => void;
}

const ProfilePictureActions = (
    {
        hasSelection,
        hasExistingAvatar,
        isBusy,
        onSelectFile,
        onClearSelection,
        onRemoveAvatar
    }: ProfilePictureActionsProps
) =>
{
    const styles = useProfilePictureDialogStyles();

    return (
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
                onClick={onClearSelection}
            >
                Discard selection
            </Button>}

            {!hasSelection && hasExistingAvatar && <Button
                id={"button-remove-profile-picture"}
                appearance={"subtle"}
                shape={"circular"}
                icon={<DeleteRegular/>}
                disabled={isBusy}
                onClick={onRemoveAvatar}
            >
                Remove photo
            </Button>}
        </div>
    );
};

export default ProfilePictureActions;
