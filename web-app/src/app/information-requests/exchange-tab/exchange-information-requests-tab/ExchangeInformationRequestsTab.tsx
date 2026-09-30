import {useState} from "react";
import {useNavigate} from "react-router-dom";
import {Button, MessageBar, MessageBarBody, Spinner, Text, Title3} from "@fluentui/react-components";
import {AddRegular} from "@fluentui/react-icons";
import CreateInformationRequestDialog from "../../authoring/create-information-request-dialog/CreateInformationRequestDialog.tsx";
import {standingReasonSentence} from "../../shared/executionStandingText.ts";
import {informationRequestManagePath} from "../../shared/informationRequestWorkspacePaths.ts";
import InformationRequestSummaryRow from "../information-request-summary-row/InformationRequestSummaryRow.tsx";
import {ExchangeInformationRequestsState} from "../useExchangeInformationRequests.ts";
import {useInformationRequestWorkspaceOpener} from "../useInformationRequestWorkspaceOpener.ts";
import {useExchangeInformationRequestsTabStyles} from "./ExchangeInformationRequestsTabStyles.tsx";

interface Props
{
    exchangeId: string;
    state: ExchangeInformationRequestsState;
}

const ExchangeInformationRequestsTab = ({exchangeId, state}: Props) =>
{
    const styles = useExchangeInformationRequestsTabStyles();
    const navigate = useNavigate();
    const {open, openingId} = useInformationRequestWorkspaceOpener();
    const [creating, setCreating] = useState(false);
    const requests = state.listing?.requests ?? [];

    return (
        <section id={"exchange-information-requests-tab"}
                 aria-labelledby={"exchange-information-requests-title"}
                 className={styles.root}>
            <div id={"exchange-information-requests-header"}
                 className={styles.header}>
                <Title3 id={"exchange-information-requests-title"}>Information Requests</Title3>
                {state.listing?.canCreate && (
                    <Button id={"exchange-information-requests-create"}
                            appearance={"primary"}
                            shape={"circular"}
                            icon={<AddRegular/>}
                            onClick={() => setCreating(true)}>
                        New Information Request
                    </Button>
                )}
            </div>
            {state.listing?.creationUnavailableReason && (
                <MessageBar id={"exchange-information-requests-creation-unavailable"}
                            intent={"info"}>
                    <MessageBarBody>
                        {`New Information Requests cannot be created here right now. ${standingReasonSentence[state.listing.creationUnavailableReason]}`}
                    </MessageBarBody>
                </MessageBar>
            )}
            {state.error && (
                <MessageBar id={"exchange-information-requests-error"}
                            intent={"error"}>
                    <MessageBarBody>{state.error}</MessageBarBody>
                </MessageBar>
            )}
            {state.loading && !state.listing && (
                <Spinner id={"exchange-information-requests-loading"}
                         size={"small"}
                         label={"Loading Information Requests"}/>
            )}
            {state.listing && requests.length === 0 && (
                <Text id={"exchange-information-requests-empty"}
                      className={styles.empty}>
                    No Information Requests in this Exchange are shared with you.
                </Text>
            )}
            {requests.length > 0 && (
                <ul id={"exchange-information-requests-list"}
                    aria-label={"Information Requests in this Exchange"}
                    className={styles.list}>
                    {requests.map(summary => (
                        <InformationRequestSummaryRow key={summary.id}
                                                      summary={summary}
                                                      opening={openingId === summary.id}
                                                      onOpen={open}/>
                    ))}
                </ul>
            )}
            <CreateInformationRequestDialog open={creating}
                                            exchangeId={exchangeId}
                                            onDismiss={() => setCreating(false)}
                                            onCreated={request =>
                                            {
                                                setCreating(false);
                                                navigate(informationRequestManagePath(request.id));
                                            }}/>
        </section>
    );
};

export default ExchangeInformationRequestsTab;
