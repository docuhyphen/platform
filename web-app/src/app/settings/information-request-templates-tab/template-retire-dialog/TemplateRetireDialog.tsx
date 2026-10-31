import {MessageBar, MessageBarBody, Text} from "@fluentui/react-components";
import EditorDialog from "../../../information-requests/shared/editor-dialog/EditorDialog.tsx";

interface TemplateRetireDialogProps
{
    versionNumber: number;
    lastPublished: boolean;
    draftVersionNumber?: number;
    busy: boolean;
    refusal: string | null;
    onConfirm: () => void;
    onDismiss: () => void;
}

const lastVersionText = (versionNumber: number, draftVersionNumber?: number): string =>
    draftVersionNumber === undefined
        ? `Version ${versionNumber} is the only published version and no draft is open, so retiring it ends this ` +
        "Template: no request, new draft or copy can start from it again."
        : `Version ${versionNumber} is the only published version, so no request can start from this Template ` +
        `until draft version ${draftVersionNumber} is published.`;

const TemplateRetireDialog = ({
                                  versionNumber,
                                  lastPublished,
                                  draftVersionNumber,
                                  busy,
                                  refusal,
                                  onConfirm,
                                  onDismiss,
                              }: TemplateRetireDialogProps) => (
    <EditorDialog id={"information-request-template-retire-dialog"}
                  title={`Retire version ${versionNumber}?`}
                  busy={busy}
                  confirmLabel={"Retire"}
                  onConfirm={onConfirm}
                  onDismiss={onDismiss}>
        {refusal && (
            <MessageBar id={"information-request-template-retire-refusal"}
                        intent={"error"}
                        role={"alert"}>
                <MessageBarBody id={"information-request-template-retire-refusal-body"}>{refusal}</MessageBarBody>
            </MessageBar>
        )}
        <Text id={"information-request-template-retire-summary"}>
            No new request can start from a retired version. Requests that already use it keep it, and Blueprints
            that name it must choose another published version before they create requests again.
        </Text>
        {lastPublished && (
            <Text id={"information-request-template-retire-last-version"}
                  weight={"semibold"}>
                {lastVersionText(versionNumber, draftVersionNumber)}
            </Text>
        )}
    </EditorDialog>
);

export default TemplateRetireDialog;
