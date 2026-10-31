import {Button} from "@fluentui/react-components";
import {InformationRequestAccessLinkDto} from "../../../models/models.tsx";
import {PartyAccessLink} from "../party-row/partyAccessLink.ts";

interface Props {
    idPrefix: string;
    label: string;
    current?: PartyAccessLink;
    busy: boolean;
    canIssue: boolean;
    onCreate: () => void;
    onResend: (link: InformationRequestAccessLinkDto) => void;
    onRevoke: (link: InformationRequestAccessLinkDto) => void;
}

const PartyLinkActions = ({idPrefix, label, current, busy, canIssue, onCreate, onResend, onRevoke}: Props) => {
    if (!current) {
        return canIssue ? (
            <Button id={`${idPrefix}-link-create`}
                    size={"small"}
                    shape={"circular"}
                    aria-label={`Create link for ${label}`}
                    disabled={busy}
                    onClick={onCreate}>
                Create link
            </Button>
        ) : null;
    }

    return (
        <>
            {canIssue && !current.expired && (
                <Button id={`${idPrefix}-link-resend`}
                        size={"small"}
                        shape={"circular"}
                        aria-label={`Resend link to ${label}`}
                        disabled={busy}
                        onClick={() => onResend(current.link)}>
                    Resend link
                </Button>
            )}
            <Button id={`${idPrefix}-link-revoke`}
                    size={"small"}
                    shape={"circular"}
                    appearance={"subtle"}
                    aria-label={`Revoke link for ${label}`}
                    disabled={busy}
                    onClick={() => onRevoke(current.link)}>
                Revoke link
            </Button>
        </>
    );
};

export default PartyLinkActions;
