import {useState} from "react";
import {Button, Text, Title3} from "@fluentui/react-components";
import {AddRegular, ArrowClockwiseRegular} from "@fluentui/react-icons";
import {InformationRequestOwnerFields} from "../../shared/ownerWorkspace.ts";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestAccessLinkDto,
    InformationRequestPartyDto,
    ReassignInformationRequestPartyRequest,
} from "../../../models/models.tsx";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import AddPartyDialog from "../add-party-dialog/AddPartyDialog.tsx";
import {PartyActionPermissions} from "../author-workspace-content/authorActionPermissions.ts";
import PartyRow from "../party-row/PartyRow.tsx";
import ReassignPartyDialog from "../reassign-party-dialog/ReassignPartyDialog.tsx";
import {usePartyPanelStyles} from "./PartyPanelStyles.tsx";
import {useDialogCommand} from "../../shared/useDialogCommand.ts";

interface Props
{
    requestId: string;
    owner: InformationRequestOwnerFields | null;
    parties: InformationRequestPartyDto[];
    links: InformationRequestAccessLinkDto[] | null;
    linksError: string | null;
    busy: boolean;
    error?: string | null;
    notice?: string | null;
    permissions: PartyActionPermissions;
    onRefresh: () => void;
    onAssign: (request: AssignInformationRequestPartyRequest) => unknown;
    onAssignSubject: (request: AssignInformationRequestSubjectRequest) => unknown;
    onIssueLink: (party: InformationRequestPartyDto) => void;
    onResendLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRevokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onReassign: (party: InformationRequestPartyDto, request: ReassignInformationRequestPartyRequest) => unknown;
    onRemove: (party: InformationRequestPartyDto) => void;
}

const PartyPanel = (props: Props) =>
{
    const styles = usePartyPanelStyles();
    const [adding, setAdding] = useState(false);
    const [reassigning, setReassigning] = useState<InformationRequestPartyDto | null>(null);
    const command = useDialogCommand(props.error, props.notice);
    const active = props.parties.filter(party => party.active);

    return (
        <section id={"information-request-parties"}
                 aria-labelledby={"information-request-parties-title"}
                 className={styles.panel}>
            <div id={"information-request-parties-header"}
                 className={styles.header}>
                <Title3 id={"information-request-parties-title"}
                        as={"h2"}>
                    Parties
                </Title3>
                <div id={"information-request-parties-actions"}
                     className={styles.headerActions}>
                    <Button id={"information-request-parties-refresh"}
                            appearance={"subtle"}
                            shape={"circular"}
                            icon={<ArrowClockwiseRegular/>}
                            disabled={props.busy}
                            onClick={props.onRefresh}>
                        Refresh
                    </Button>
                    {props.permissions.change && (
                        <Button id={"information-request-parties-add"}
                                appearance={"secondary"}
                                shape={"circular"}
                                icon={<AddRegular/>}
                                disabled={props.busy}
                                onClick={() => {
                                    command.reset();
                                    setAdding(true);
                                }}>
                            Add party
                        </Button>
                    )}
                </div>
            </div>
            {props.linksError && (
                <InformationRequestLoadError id={"information-request-parties-links-load-error"}
                                             message={props.linksError}
                                             onRetry={props.onRefresh}/>
            )}
            {active.length === 0 && (
                <Text id={"information-request-parties-empty"}
                      className={styles.muted}>
                    No one is named on this request yet.
                </Text>
            )}
            {active.length > 0 && (
                <ul id={"information-request-parties-list"}
                    aria-label={"Parties"}
                    className={styles.list}>
                    {active.map(party => (
                        <PartyRow key={party.id}
                                  party={party}
                                  links={props.links ? props.links.filter(link => link.partyId === party.id) : null}
                                  busy={props.busy}
                                  permissions={props.permissions}
                                  onIssueLink={props.onIssueLink}
                                  onResendLink={props.onResendLink}
                                  onRevokeLink={props.onRevokeLink}
                                  onReassign={party => {
                                      command.reset();
                                      setReassigning(party);
                                  }}
                                  onRemove={props.onRemove}/>
                    ))}
                </ul>
            )}
            {adding && (
                <AddPartyDialog requestId={props.requestId}
                                owner={props.owner}
                                busy={props.busy || command.busy}
                                commandError={command.error}
                                onAssign={request => void command.run(() => props.onAssign(request), () => setAdding(false))}
                                onAssignSubject={request => void command.run(() => props.onAssignSubject(request), () => setAdding(false))}
                                onDismiss={() => setAdding(false)}/>
            )}
            {reassigning && (
                <ReassignPartyDialog requestId={props.requestId}
                                     owner={props.owner}
                                     party={reassigning}
                                     busy={props.busy || command.busy}
                                     commandError={command.error}
                                     onReassign={request => void command.run(() => props.onReassign(reassigning, request), () => setReassigning(null))}
                                     onDismiss={() => setReassigning(null)}/>
            )}
        </section>
    );
};

export default PartyPanel;
