import {Button, Text} from "@fluentui/react-components";
import {
    InformationRequestAccessLinkDto,
    InformationRequestAccessLinkStatus,
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";
import {usePartyRowStyles} from "./PartyRowStyles.tsx";

interface Props
{
    party: InformationRequestPartyDto;
    links: InformationRequestAccessLinkDto[];
    busy: boolean;
    editable: boolean;
    onIssueLink: (party: InformationRequestPartyDto) => void;
    onResendLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRevokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRemove: (party: InformationRequestPartyDto) => void;
}

const linkStatus = (link?: InformationRequestAccessLinkDto): string =>
{
    if (!link) return "No access link";
    return link.rotationCount > 0 ? `Access link active, resent ${link.rotationCount} times` : "Access link active";
};

const PartyRow = ({party, links, busy, editable, onIssueLink, onResendLink, onRevokeLink, onRemove}: Props) =>
{
    const styles = usePartyRowStyles();
    const label = partyLabel(party);
    const acting = party.roleKey !== InformationRequestShareRoleKey.SUBJECT && Boolean(party.principalId);
    const activeLink = links.find(link => link.status === InformationRequestAccessLinkStatus.ACTIVE);
    const idPrefix = `information-request-party-${party.id}`;

    return (
        <li id={idPrefix}
            className={styles.row}>
            <div id={`${idPrefix}-identity`}
                 className={styles.identity}>
                <Text id={`${idPrefix}-label`}
                      weight={"semibold"}
                      className={styles.label}>
                    {label}
                </Text>
                <Text id={`${idPrefix}-meta`}
                      size={200}
                      className={styles.meta}>
                    {acting ? `${shareRoleLabels[party.roleKey]}. ${linkStatus(activeLink)}` : shareRoleLabels[party.roleKey]}
                </Text>
            </div>
            {editable && (
                <div id={`${idPrefix}-actions`}
                     className={styles.actions}>
                    {acting && !activeLink && (
                        <Button id={`${idPrefix}-link-create`}
                                size={"small"}
                                shape={"circular"}
                                aria-label={`Create link for ${label}`}
                                disabled={busy}
                                onClick={() => onIssueLink(party)}>
                            Create link
                        </Button>
                    )}
                    {acting && activeLink && (
                        <Button id={`${idPrefix}-link-resend`}
                                size={"small"}
                                shape={"circular"}
                                aria-label={`Resend link to ${label}`}
                                disabled={busy}
                                onClick={() => onResendLink(party, activeLink)}>
                            Resend link
                        </Button>
                    )}
                    {acting && activeLink && (
                        <Button id={`${idPrefix}-link-revoke`}
                                size={"small"}
                                shape={"circular"}
                                appearance={"subtle"}
                                aria-label={`Revoke link for ${label}`}
                                disabled={busy}
                                onClick={() => onRevokeLink(party, activeLink)}>
                            Revoke link
                        </Button>
                    )}
                    <Button id={`${idPrefix}-remove`}
                            size={"small"}
                            shape={"circular"}
                            appearance={"subtle"}
                            aria-label={`Remove ${label}`}
                            disabled={busy}
                            onClick={() => onRemove(party)}>
                        Remove
                    </Button>
                </div>
            )}
        </li>
    );
};

export default PartyRow;
