import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestDto, InformationRequestState} from "../../../models/models.tsx";
import {LifecycleActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import CancelRequestDialog from "../cancel-request-dialog/CancelRequestDialog.tsx";
import SupersedeRequestDialog from "../supersede-request-dialog/SupersedeRequestDialog.tsx";
import {useRequestLifecycleActionsStyles} from "./RequestLifecycleActionsStyles.tsx";
import {useDialogCommand} from "../../shared/useDialogCommand.ts";

interface Props
{
    request: InformationRequestDto;
    permissions: LifecycleActionPermissions;
    busy: boolean;
    error?: string | null;
    notice?: string | null;
    onIssue: () => void;
    onCancel: (reasonCode: string) => unknown;
    onSupersede: (replacementId: string, reasonCode: string) => unknown;
}

const OPEN_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

const RequestLifecycleActions = ({request, permissions, busy, error, notice, onIssue, onCancel, onSupersede}: Props) =>
{
    const styles = useRequestLifecycleActionsStyles();
    const [dialog, setDialog] = useState<"cancel" | "supersede" | null>(null);
    const command = useDialogCommand(error, notice);
    const draft = request.state === InformationRequestState.DRAFT;
    const open = OPEN_STATES.has(request.state);

    if (!draft && !open)
    {
        return (
            <Text id={"information-request-lifecycle-closed"}
                  className={styles.muted}>
                This request no longer accepts changes. Its history is kept.
            </Text>
        );
    }

    if (!permissions.issue && !permissions.cancel && !permissions.supersede) return null;

    return (
        <div id={"information-request-lifecycle-actions"}
             role={"group"}
             aria-label={"Request actions"}
             className={styles.actions}>
            {permissions.issue && (
                <Button id={"information-request-issue"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={onIssue}>
                    Issue
                </Button>
            )}
            {permissions.supersede && (
                <Button id={"information-request-supersede"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={() => {
                            command.reset();
                            setDialog("supersede");
                        }}>
                    Supersede
                </Button>
            )}
            {permissions.cancel && (
                <Button id={"information-request-cancel"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={() => {
                            command.reset();
                            setDialog("cancel");
                        }}>
                    Cancel request
                </Button>
            )}
            {dialog === "cancel" && (
                <CancelRequestDialog busy={busy || command.busy}
                                     error={command.error}
                                     onConfirm={reason => void command.run(() => onCancel(reason), () => setDialog(null))}
                                     onDismiss={() => setDialog(null)}/>
            )}
            {dialog === "supersede" && (
                <SupersedeRequestDialog requestId={request.id}
                                        exchangeId={request.exchangeId}
                                        busy={busy || command.busy}
                                        error={command.error}
                                        onConfirm={(replacementId, reason) => void command.run(() => onSupersede(replacementId, reason), () => setDialog(null))}
                                        onDismiss={() => setDialog(null)}/>
            )}
        </div>
    );
};

export default RequestLifecycleActions;
