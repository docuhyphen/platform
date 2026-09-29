import {Text} from "@fluentui/react-components";
import EditorDialog from "../../../information-requests/shared/editor-dialog/EditorDialog.tsx";

interface TemplateRetireDialogProps
{
    versionNumber: number;
    busy: boolean;
    onConfirm: () => void;
    onDismiss: () => void;
}

const TemplateRetireDialog = ({versionNumber, busy, onConfirm, onDismiss}: TemplateRetireDialogProps) => (
    <EditorDialog id={"information-request-template-retire-dialog"}
                  title={`Retire version ${versionNumber}?`}
                  busy={busy}
                  confirmLabel={"Retire"}
                  onConfirm={onConfirm}
                  onDismiss={onDismiss}>
        <Text id={"information-request-template-retire-summary"}>
            No new request can start from a retired version. Requests that already use it keep it, and Blueprints
            that name it must choose another published version before they create requests again.
        </Text>
    </EditorDialog>
);

export default TemplateRetireDialog;
