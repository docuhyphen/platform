import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {InformationRequestDto, InformationRequestState} from "../../../models/models.tsx";
import CancelRequestDialog from "../cancel-request-dialog/CancelRequestDialog.tsx";
import SupersedeRequestDialog from "../supersede-request-dialog/SupersedeRequestDialog.tsx";
import {useRequestLifecycleActionsStyles} from "./RequestLifecycleActionsStyles.tsx";

interface Props
{
    request: InformationRequestDto;
    busy: boolean;
    onIssue: () => void;
    onCancel: (reasonCode: string) => void;
    onSupersede: (replacementId: string, reasonCode: string) => void;
}

const OPEN_STATES = new Set([InformationRequestState.ISSUED, InformationRequestState.IN_PROGRESS]);

const RequestLifecycleActions = ({request, busy, onIssue, onCancel, onSupersede}: Props) =>
{
    const styles = useRequestLifecycleActionsStyles();
    const [dialog, setDialog] = useState<"cancel" | "supersede" | null>(null);
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

    return (
        <div id={"information-request-lifecycle-actions"}
             role={"group"}
             aria-label={"Request actions"}
             className={styles.actions}>
            {draft && (
                <Button id={"information-request-issue"}
                        appearance={"primary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={onIssue}>
                    Issue
                </Button>
            )}
            {open && (
                <Button id={"information-request-supersede"}
                        appearance={"secondary"}
                        shape={"circular"}
                        disabled={busy}
                        onClick={() => setDialog("supersede")}>
                    Supersede
                </Button>
            )}
            <Button id={"information-request-cancel"}
                    appearance={"secondary"}
                    shape={"circular"}
                    disabled={busy}
                    onClick={() => setDialog("cancel")}>
                Cancel request
            </Button>
            {dialog === "cancel" && (
                <CancelRequestDialog busy={busy}
                                     onConfirm={reason =>
                                     {
                                         setDialog(null);
                                         onCancel(reason);
                                     }}
                                     onDismiss={() => setDialog(null)}/>
            )}
            {dialog === "supersede" && (
                <SupersedeRequestDialog requestId={request.id}
                                        exchangeId={request.exchangeId}
                                        busy={busy}
                                        onConfirm={(replacementId, reason) =>
                                        {
                                            setDialog(null);
                                            onSupersede(replacementId, reason);
                                        }}
                                        onDismiss={() => setDialog(null)}/>
            )}
        </div>
    );
};

export default RequestLifecycleActions;
