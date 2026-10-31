import {Badge, Button, Text} from "@fluentui/react-components";
import {
    InformationRequestAccessLinkDto,
    InformationRequestPartyDto,
    InformationRequestShareRoleKey,
} from "../../../models/models.tsx";
import {shareRoleLabels} from "../../shared/informationRequestLabels.ts";
import {partyLabel} from "../author-workspace/useAuthorWorkspace.ts";
import {PartyActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import PartyLinkActions from "../party-link-actions/PartyLinkActions.tsx";
import {currentAccessLink, partyAccessStatus} from "./partyAccessLink.ts";
import {usePartyRowStyles} from "./PartyRowStyles.tsx";

interface Props
{
    party: InformationRequestPartyDto;
    links: InformationRequestAccessLinkDto[] | null;
    busy: boolean;
    permissions: PartyActionPermissions;
    onIssueLink: (party: InformationRequestPartyDto) => void;
    onResendLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRevokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onReassign: (party: InformationRequestPartyDto) => void;
    onRemove: (party: InformationRequestPartyDto) => void;
}

const PartyRow = ({
                      party,
                      links,
                      busy,
                      permissions,
                      onIssueLink,
                      onResendLink,
                      onRevokeLink,
                      onReassign,
                      onRemove
                  }: Props) =>
{
    const styles = usePartyRowStyles();
    const label = partyLabel(party);
    const acting = party.roleKey !== InformationRequestShareRoleKey.SUBJECT && Boolean(party.principalId);
    const current = links ? currentAccessLink(links) : undefined;
    const linkUnknown = links === null && party.principalKind === "PARTICIPANT" && !party.accountLinked;
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
                    {acting && !linkUnknown
                        ? `${shareRoleLabels[party.roleKey]}. ${partyAccessStatus(party, current)}`
                        : shareRoleLabels[party.roleKey]}
                </Text>
                {party.trustSuspended && (
                    <>
                        <Badge id={`${idPrefix}-trust-suspended`}
                               appearance={"outline"}
                               color={"warning"}
                               className={styles.trustBadge}>
                            Trusted relationship suspended
                        </Badge>
                        <Text id={`${idPrefix}-trust-suspended-note`}
                              size={200}
                              className={styles.meta}>
                            This party keeps answering what was already issued. New trusted assignments are paused until
                            the relationship is restored.
                        </Text>
                    </>
                )}
            </div>
            {permissions.reduceAccess && (
                <div id={`${idPrefix}-actions`}
                     className={styles.actions}>
                    {acting && party.principalKind === "PARTICIPANT" && !party.accountLinked && !linkUnknown && (
                        <PartyLinkActions idPrefix={idPrefix}
                                          label={label}
                                          current={current}
                                          busy={busy}
                                          canIssue={permissions.issueLinks}
                                          onCreate={() => onIssueLink(party)}
                                          onResend={link => onResendLink(party, link)}
                                          onRevoke={link => onRevokeLink(party, link)}/>
                    )}
                    {acting && permissions.change && (
                        <Button id={`${idPrefix}-reassign`}
                                size={"small"}
                                shape={"circular"}
                                aria-label={`Reassign ${label}`}
                                disabled={busy}
                                onClick={() => onReassign(party)}>
                            Reassign
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
