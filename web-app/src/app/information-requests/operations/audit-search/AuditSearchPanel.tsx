import {Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import AuditEventRow from "../audit-event-row/AuditEventRow.tsx";
import AuditSearchFilters from "../audit-search-filters/AuditSearchFilters.tsx";
import {AUDIT_SEARCH_PAGE_SIZE, useAuditSearch} from "./useAuditSearch.ts";
import {useAuditSearchPanelStyles} from "./AuditSearchPanelStyles.tsx";
import {formatInformationRequestCount} from "../../shared/informationRequestFormatting.ts";

const foundSentence = (total: number): string => `${formatInformationRequestCount(total)} ${total === 1 ? "event" : "events"} found.`;

const AuditSearchPanel = () =>
{
    const styles = useAuditSearchPanelStyles();
    const state = useAuditSearch();
    const {page, offset} = state;

    return (
        <section id={"information-request-audit-search"}
                 aria-labelledby={"information-request-audit-search-title"}
                 className={styles.panel}>
            <Text id={"information-request-audit-search-title"}
                  as={"h2"}
                  size={500}
                  weight={"semibold"}
                  className={styles.heading}>
                Audit search
            </Text>
            <Text id={"information-request-audit-search-explanation"}
                  className={styles.muted}>
                Search the audit record of every Information Request this account owns. Values that are not safe to
                show are counted as withheld.
            </Text>
            <AuditSearchFilters form={state.form}
                                problem={state.problem}
                                onChange={state.change}
                                onSearch={state.search}/>
            {page.error && (
                <MessageBar id={"information-request-audit-search-error"}
                            intent={"error"}
                            role={"alert"}>
                    <MessageBarBody>{page.error}</MessageBarBody>
                </MessageBar>
            )}
            <Text id={"information-request-audit-search-status"}
                  role={"status"}
                  aria-live={"polite"}>
                {page.value ? foundSentence(page.value.total) : ""}
            </Text>
            {!page.error && !page.value && (
                <Spinner id={"information-request-audit-search-loading"}
                         size={"medium"}
                         label={"Searching the audit record"}/>
            )}
            {page.value && page.value.items.length > 0 && (
                <ul id={"information-request-audit-search-results"}
                    aria-label={"Audit events"}
                    className={styles.list}>
                    {page.value.items.map(event => (
                        <AuditEventRow key={event.eventId}
                                       event={event}
                                       showRequest={true}/>
                    ))}
                </ul>
            )}
            {page.value && page.value.total > AUDIT_SEARCH_PAGE_SIZE && (
                <div id={"information-request-audit-search-pager"}
                     className={styles.pager}>
                    <Button id={"information-request-audit-search-previous"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset === 0}
                            onClick={() => state.setOffset(Math.max(0, offset - AUDIT_SEARCH_PAGE_SIZE))}>
                        Previous
                    </Button>
                    <Text id={"information-request-audit-search-range"}>
                        {`${formatInformationRequestCount(offset + 1)} to ${formatInformationRequestCount(Math.min(offset + AUDIT_SEARCH_PAGE_SIZE, page.value.total))} of ${formatInformationRequestCount(page.value.total)}`}
                    </Text>
                    <Button id={"information-request-audit-search-next"}
                            appearance={"secondary"}
                            shape={"circular"}
                            disabled={offset + AUDIT_SEARCH_PAGE_SIZE >= page.value.total}
                            onClick={() => state.setOffset(offset + AUDIT_SEARCH_PAGE_SIZE)}>
                        Next
                    </Button>
                </div>
            )}
        </section>
    );
};

export default AuditSearchPanel;
