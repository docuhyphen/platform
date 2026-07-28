import {
    Button,
    Dialog,
    DialogActions,
    DialogBody,
    DialogContent,
    DialogSurface,
    DialogTitle,
    DialogTrigger,
    Field,
    Spinner,
} from "@fluentui/react-components";
import {PersonAddRegular} from "@fluentui/react-icons";
import MultiPersonPicker from "../../../components/person-picker/multi-person-picker/MultiPersonPicker.tsx";
import {useAddAdminDialog} from "./useAddAdminDialog.ts";
import {useAddAdminDialogStyles} from "./AddAdminDialogStyles.tsx";

interface AddAdminDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
    existingAdminUserIds: Set<string>;
    onComplete: () => void;
}

const AddAdminDialog = (props: AddAdminDialogProps) =>
{
    const styles = useAddAdminDialogStyles();
    const state = useAddAdminDialog(props);

    return (
        <Dialog
            modalType={"alert"}
            open={props.isOpen}>
            <DialogSurface
                id={"add-app-administrator-dialog"}
                className={styles.surface}>
                <DialogBody id={"add-app-administrator-dialog-body"}>
                    <DialogTitle id={"add-app-administrator-dialog-title"}>
                        Add App Administrators
                    </DialogTitle>
                    <DialogContent
                        id={"add-app-administrator-dialog-content"}
                        className={styles.content}>
                        {state.error && (
                            <div
                                id={"add-app-administrator-error"}
                                className={styles.errorText}>
                                {state.error}
                            </div>
                        )}
                        <Field
                            id={"add-app-administrator-search-field"}
                            label={"Search users by name or email"}>
                            <MultiPersonPicker
                                id={"input-admin-search"}
                                people={state.searchResultItems}
                                selectedPeople={state.selectedUserItems}
                                onSelectionChange={state.onSelectionChange}
                                query={state.searchQuery}
                                onQueryChange={state.setSearchQuery}
                                placeholder={"Type at least 2 characters"}
                                disabled={state.busy}
                                noResultsText={"No matching users found"}/>
                        </Field>
                        {state.searching && (
                            <Spinner
                                id={"add-app-administrator-searching"}
                                size={"tiny"}
                                label={"Searching..."}/>
                        )}
                    </DialogContent>
                </DialogBody>
                <DialogActions id={"add-app-administrator-dialog-actions"}>
                    <Button
                        id={"btn-dialog-add-admin"}
                        appearance={"primary"}
                        shape={"circular"}
                        icon={state.busy ? <Spinner size={"tiny"}/> : <PersonAddRegular/>}
                        disabled={state.busy || state.selectedCount === 0}
                        onClick={state.handleAdd}>
                        {state.submitLabel}
                    </Button>
                    <DialogTrigger disableButtonEnhancement>
                        <Button
                            id={"btn-dialog-cancel"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={state.busy}
                            onClick={state.handleClose}>
                            Cancel
                        </Button>
                    </DialogTrigger>
                </DialogActions>
            </DialogSurface>
        </Dialog>
    );
};

export default AddAdminDialog;
