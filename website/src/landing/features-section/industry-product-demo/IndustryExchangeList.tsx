import {mergeClasses} from "@fluentui/react-components";
import {
    Archive16Regular,
    ArrowSort16Regular,
    Filter16Regular,
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
                <div
                    id="industry-demo-exchange-statuses"
                    className={styles.statuses}
                >
                    <span
                        id="industry-demo-requests-status"
                        className={styles.status}
                    >
                        Requests
                        <span
                            id="industry-demo-request-count"
                            className={styles.requestCount}
                        >
                            3
                        </span>
                    </span>
                    <span
                        id="industry-demo-active-status"
                        className={mergeClasses(styles.status, styles.activeStatus)}
                    >
                        Active
                    </span>
                    <span
                        id="industry-demo-archive-status"
                        className={styles.status}
                    >
                        <Archive16Regular
                            id="industry-demo-archive-status-icon"
                            aria-hidden="true"
                        />
                        Archive
                    </span>
                </div>
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
