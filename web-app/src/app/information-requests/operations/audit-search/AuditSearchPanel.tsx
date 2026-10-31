import {Spinner, Text} from "@fluentui/react-components";
import InformationRequestLoadError from "../../shared/load-error/InformationRequestLoadError.tsx";
import AuditEventRow from "../audit-event-row/AuditEventRow.tsx";
import AuditPager from "../audit-pager/AuditPager.tsx";
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
                <InformationRequestLoadError id={"information-request-audit-search-error"}
                                             message={page.error}
                                             onRetry={page.reload}/>
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
                                       showRequest={true}
                                       onFilter={state.filterBy}/>
                    ))}
                </ul>
            )}
            {page.value && (
                <AuditPager idPrefix={"information-request-audit-search"}
                            offset={offset}
                            pageSize={AUDIT_SEARCH_PAGE_SIZE}
                            total={page.value.total}
                            onOffsetChange={state.setOffset}/>
            )}
        </section>
    );
};

export default AuditSearchPanel;
