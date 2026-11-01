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
    Input,
    Spinner
} from "@fluentui/react-components";
import React from "react";
import {useBasicDetailsEditDialogStyles} from "./BasicDetailsEditDialogStyles.tsx";
import {useGlobalStyles} from "../../../../GlobalStyles.tsx";
import {useBasicDetailsForm} from "./useBasicDetailsForm.ts";

interface BasicDetailsEditDialogProps
{
    isOpen: boolean;
    onDismiss: () => void;
}

const BasicDetailsEditDialog: React.FC<BasicDetailsEditDialogProps> = (
    {
        isOpen,
        onDismiss
    }
) =>
{
    const styles = useBasicDetailsEditDialogStyles();
    const globalStyles = useGlobalStyles();
    const form = useBasicDetailsForm(onDismiss);

    return <Dialog modalType="alert"
                   open={isOpen}>
        <DialogSurface id={"basic-details-edit-dialog-dialog-surface-1"}>
            <DialogBody id={"basic-details-edit-dialog-dialog-body-1"}>
                <DialogTitle id={"basic-details-edit-dialog-dialog-title-1"}>Update your profile</DialogTitle>
                <DialogContent id={"basic-details-edit-dialog-dialog-content-1"}
                               className={styles.dialogContentContainer}>
                    {form.error && <div id={"basic-details-edit-dialog-div-1"}
                                        className={styles.errorContainer}>{form.error}</div>}

                    <Field id={"basic-details-edit-dialog-field-1"}
                           label={"Your first name"}>
                        <Input id={"input-first-name"}
                               type={"text"}
                               value={form.firstName}
                               maxLength={30}
                               onChange={(e) => form.setFirstName(e.target.value)}
                               onKeyDown={(e) => { if (e.key === "Enter") form.onUpdate(); }}/>
                    </Field>

                    <Field id={"basic-details-edit-dialog-field-2"}
                           label={"Your last name"}>
                        <Input id={"input-last-name"}
                               type={"text"}
                               value={form.lastName}
                               maxLength={30}
                               onChange={(e) => form.setLastName(e.target.value)}
                               onKeyDown={(e) => { if (e.key === "Enter") form.onUpdate(); }}/>
                    </Field>
                </DialogContent>
            </DialogBody>
            <DialogActions id={"basic-details-edit-dialog-dialog-actions-1"}>
                <Button id={"button-update-profile"}
                        appearance="primary"
                        shape={"circular"}
                        className={globalStyles.buttonWithLoading}
                        disabled={form.updatingProfile || form.isUnchanged}
                        onClick={form.onUpdate}>
                    {form.updatingProfile && <Spinner id={"basic-details-edit-dialog-spinner-1"}
                                                      size={"tiny"}/>}
                    {form.updatingProfile ? "Updating…" : "Update"}
                </Button>
                <DialogTrigger disableButtonEnhancement>
                    <Button id={"button-basic-details-close"}
                            appearance="secondary"
                            shape={"circular"}
                            disabled={form.updatingProfile}
                            onClick={form.onClose}>
                        Close
                    </Button>
                </DialogTrigger>
            </DialogActions>
        </DialogSurface>
    </Dialog>;
};

export default BasicDetailsEditDialog;
