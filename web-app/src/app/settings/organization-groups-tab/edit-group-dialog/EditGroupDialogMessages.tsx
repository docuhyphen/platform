import {Button, MessageBar, MessageBarActions, MessageBarBody, MessageBarTitle} from "@fluentui/react-components";
import {DismissRegular} from "@fluentui/react-icons";

interface EditGroupDialogMessagesProps
{
    permissionDenied: boolean;
    error: string | null;
    onDismissError: () => void;
}

const EditGroupDialogMessages = ({permissionDenied, error, onDismissError}: EditGroupDialogMessagesProps) => (
    <>
        {permissionDenied &&
            <MessageBar id={"edit-group-dialog-message-bar-1"}
                        intent="info">
                <MessageBarBody id={"edit-group-dialog-message-bar-body-1"}>
                    <MessageBarTitle id={"edit-group-dialog-message-bar-title-1"}>No permission</MessageBarTitle>
                    You don't have permission to manage this group's members.
                    Ask a group OWNER or MANAGER to make changes.
                </MessageBarBody>
            </MessageBar>
        }
        {error &&
            <MessageBar id={"edit-group-dialog-message-bar-2"}
                        intent="error">
                <MessageBarBody id={"edit-group-dialog-message-bar-body-2"}>
                    <MessageBarTitle id={"edit-group-dialog-message-bar-title-2"}>Error</MessageBarTitle>
                    {error}
                </MessageBarBody>
                <MessageBarActions
                    id={"edit-group-dialog-message-bar-actions-1"}
                    containerAction={
                        <Button
                            id="edit-group-dialog-dismiss-error"
                            onClick={onDismissError}
                            appearance="transparent"
                            shape={"circular"}
                            icon={<DismissRegular/>}
                        />
                    }
                />
            </MessageBar>
        }
    </>
);

export default EditGroupDialogMessages;
