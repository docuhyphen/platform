import {useState} from "react";
import {Button, Text, Title3} from "@fluentui/react-components";
import {AddRegular, ArrowClockwiseRegular} from "@fluentui/react-icons";
import {
    AssignInformationRequestPartyRequest,
    AssignInformationRequestSubjectRequest,
    InformationRequestAccessLinkDto,
    InformationRequestPartyDto,
} from "../../../models/models.tsx";
import AddPartyDialog from "../add-party-dialog/AddPartyDialog.tsx";
import PartyRow from "../party-row/PartyRow.tsx";
import {usePartyPanelStyles} from "./PartyPanelStyles.tsx";

interface Props
{
    parties: InformationRequestPartyDto[];
    links: InformationRequestAccessLinkDto[];
    busy: boolean;
    editable: boolean;
    onRefresh: () => void;
    onAssign: (request: AssignInformationRequestPartyRequest) => void;
    onAssignSubject: (request: AssignInformationRequestSubjectRequest) => void;
    onIssueLink: (party: InformationRequestPartyDto) => void;
    onResendLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRevokeLink: (party: InformationRequestPartyDto, link: InformationRequestAccessLinkDto) => void;
    onRemove: (party: InformationRequestPartyDto) => void;
}

const PartyPanel = (props: Props) =>
{
    const styles = usePartyPanelStyles();
    const [adding, setAdding] = useState(false);
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
                    {props.editable && (
                        <Button id={"information-request-parties-add"}
                                appearance={"secondary"}
                                shape={"circular"}
                                icon={<AddRegular/>}
                                disabled={props.busy}
                                onClick={() => setAdding(true)}>
                            Add party
                        </Button>
                    )}
                </div>
            </div>
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
                                  links={props.links.filter(link => link.partyId === party.id)}
                                  busy={props.busy}
                                  editable={props.editable}
                                  onIssueLink={props.onIssueLink}
                                  onResendLink={props.onResendLink}
                                  onRevokeLink={props.onRevokeLink}
                                  onRemove={props.onRemove}/>
                    ))}
                </ul>
            )}
            {adding && (
                <AddPartyDialog busy={props.busy}
                                onAssign={request =>
                                {
                                    setAdding(false);
                                    props.onAssign(request);
                                }}
                                onAssignSubject={request =>
                                {
                                    setAdding(false);
                                    props.onAssignSubject(request);
                                }}
                                onDismiss={() => setAdding(false)}/>
            )}
        </section>
    );
};

export default PartyPanel;
