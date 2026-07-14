import {CounterBadge, mergeClasses, Tab, TabList, tokens} from "@fluentui/react-components";
import {
    Archive20Regular,
    ArrowSort16Regular,
    Filter16Regular,
    Live20Filled,
    MailInbox20Regular,
    Person16Regular,
    Search16Regular,
} from "@fluentui/react-icons";
import type {IndustryExchangeExample} from "../featureContent.ts";
import {useIndustryExchangeListStyles} from "./IndustryExchangeListStyles.tsx";

interface IndustryExchangeListProps
{
    exchanges: readonly IndustryExchangeExample[];
}

export function IndustryExchangeList({exchanges}: IndustryExchangeListProps)
{
    const styles = useIndustryExchangeListStyles();

    return (
        <aside
            id="industry-demo-exchange-list"
            className={styles.sidebar}
            aria-label="Example Exchanges"
        >
            <div
                id="industry-demo-exchange-filters"
                className={styles.filters}
            >
                <TabList
                    id="industry-demo-exchange-statuses"
                    className={styles.statuses}
                    selectedValue="active"
                    size="small"
                >
                    <Tab
                        id="industry-demo-requests-status"
                        value="requests"
                    >
                        <span className={styles.tabContent}>
                            <MailInbox20Regular aria-hidden="true"/>
                            Requests
                            <CounterBadge
                                id="industry-demo-request-count"
                                count={3}
                                size="small"
                                appearance="filled"
                                color="danger"
                            />
                        </span>
                    </Tab>
                    <Tab
                        id="industry-demo-active-status"
                        value="active"
                    >
                        <span className={mergeClasses(styles.tabContent, styles.activeTabContent)}>
                            <Live20Filled
                                aria-hidden="true"
                                primaryFill={tokens.colorBrandForeground1}
                            />
                            Active
                        </span>
                    </Tab>
                    <Tab
                        id="industry-demo-archive-status"
                        value="archive"
                    >
                        <span className={styles.tabContent}>
                            <Archive20Regular
                                id="industry-demo-archive-status-icon"
                                aria-hidden="true"
                            />
                            Archive
                        </span>
                    </Tab>
                </TabList>
                <div
                    id="industry-demo-exchange-search-row"
                    className={styles.searchRow}
                >
                    <div
                        id="industry-demo-exchange-search"
                        className={styles.search}
                    >
                        <Search16Regular
                            id="industry-demo-exchange-search-icon"
                            aria-hidden="true"
                        />
                        <span id="industry-demo-exchange-search-label">
                            Search Exchanges
                        </span>
                    </div>
                    <Filter16Regular
                        id="industry-demo-exchange-filter-icon"
                        className={styles.utilityIcon}
                        aria-hidden="true"
                    />
                    <ArrowSort16Regular
                        id="industry-demo-exchange-sort-icon"
                        className={styles.utilityIcon}
                        aria-hidden="true"
                    />
                </div>
            </div>

            <div
                id="industry-demo-exchange-examples"
                className={styles.exchangeExamples}
            >
                {exchanges.map((exchange, index) => (
                    <article
                        id={`industry-demo-exchange-example-${index}`}
                        key={exchange.title}
                        className={mergeClasses(styles.exchange, index === 0 && styles.selectedExchange)}
                    >
                        <span
                            id={`industry-demo-exchange-avatar-${index}`}
                            className={styles.exchangeAvatar}
                            aria-hidden="true"
                        >
                            <Person16Regular id={`industry-demo-exchange-person-icon-${index}`}/>
                        </span>
                        <span
                            id={`industry-demo-exchange-copy-${index}`}
                            className={styles.exchangeCopy}
                        >
                            <span
                                id={`industry-demo-exchange-title-${index}`}
                                className={styles.exchangeTitle}
                            >
                                {exchange.title}
                            </span>
                            <span
                                id={`industry-demo-exchange-summary-${index}`}
                                className={styles.exchangeSummary}
                            >
                                {exchange.summary}
                            </span>
                        </span>
                        <time
                            id={`industry-demo-exchange-updated-${index}`}
                            className={styles.updated}
                        >
                            {exchange.updated}
                        </time>
                    </article>
                ))}
            </div>

            <div
                id="industry-demo-exchange-list-footer"
                className={styles.footer}
                aria-hidden="true"
            >
                ‹
            </div>
        </aside>
    );
}
