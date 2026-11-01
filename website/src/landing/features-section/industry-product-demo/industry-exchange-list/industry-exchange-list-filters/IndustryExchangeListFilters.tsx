import {CounterBadge, mergeClasses, Tab, TabList, tokens} from "@fluentui/react-components";
import {
    Archive20Regular,
    ArrowSort16Regular,
    Filter16Regular,
    Live20Filled,
    MailInbox20Regular,
    Search16Regular,
} from "@fluentui/react-icons";
import {useIndustryExchangeListFiltersStyles} from "./IndustryExchangeListFiltersStyles.tsx";

export function IndustryExchangeListFilters()
{
    const styles = useIndustryExchangeListFiltersStyles();

    return (
        <div id={"industry-demo-exchange-filters"}
             className={styles.filters}>
            <TabList id={"industry-demo-exchange-statuses"}
                     className={styles.statuses}
                     selectedValue="active"
                     size="small">
                <Tab id={"industry-demo-requests-status"}
                     value="requests">
                    <span id={"industry-demo-requests-status-content"}
                          className={styles.tabContent}>
                        <MailInbox20Regular id={"industry-demo-requests-status-icon"}
                                            aria-hidden="true"/>
                        Requests
                        <CounterBadge id={"industry-demo-request-count"}
                                      count={3}
                                      size="small"
                                      appearance="filled"
                                      color="danger"/>
                    </span>
                </Tab>
                <Tab id={"industry-demo-active-status"}
                     value="active">
                    <span id={"industry-demo-active-status-content"}
                          className={mergeClasses(styles.tabContent, styles.activeTabContent)}>
                        <Live20Filled id={"industry-demo-active-status-icon"}
                                      aria-hidden="true"
                                      primaryFill={tokens.colorBrandForeground1}/>
                        Active
                    </span>
                </Tab>
                <Tab id={"industry-demo-archive-status"}
                     value="archive">
                    <span id={"industry-demo-archive-status-content"}
                          className={styles.tabContent}>
                        <Archive20Regular id={"industry-demo-archive-status-icon"}
                                          aria-hidden="true"/>
                        Archive
                    </span>
                </Tab>
            </TabList>
            <div id={"industry-demo-exchange-search-row"}
                 className={styles.searchRow}>
                <div id={"industry-demo-exchange-search"}
                     className={styles.search}>
                    <Search16Regular id={"industry-demo-exchange-search-icon"}
                                     aria-hidden="true"/>
                    <span id={"industry-demo-exchange-search-label"}>
                        Search Exchanges
                    </span>
                </div>
                <Filter16Regular id={"industry-demo-exchange-filter-icon"}
                                 className={styles.utilityIcon}
                                 aria-hidden="true"/>
                <ArrowSort16Regular id={"industry-demo-exchange-sort-icon"}
                                    className={styles.utilityIcon}
                                    aria-hidden="true"/>
            </div>
        </div>
    );
}
