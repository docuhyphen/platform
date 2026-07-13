import {Button, DialogActions} from "@fluentui/react-components";

interface AuditExportRequestActionsProps
{
    disabled: boolean;
    onSubmit: () => void;
    onDismiss: () => void;
}

const AuditExportRequestActions = (props: AuditExportRequestActionsProps) => (
    <DialogActions id={"audit-export-request-actions"}>
        <Button
            id={"button-audit-export-request-submit"}
            appearance={"primary"}
            shape={"circular"}
            disabled={props.disabled}
            onClick={props.onSubmit}
        >Request export</Button>
        <Button
            id={"button-audit-export-request-cancel"}
            appearance={"secondary"}
            shape={"circular"}
            onClick={props.onDismiss}
        >Cancel</Button>
    </DialogActions>
);

export default AuditExportRequestActions;
