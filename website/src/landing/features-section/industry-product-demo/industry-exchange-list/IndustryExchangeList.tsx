import {mergeClasses} from "@fluentui/react-components";
import {Person16Regular} from "@fluentui/react-icons";
import type {IndustryExchangeExample} from "../../featureContent.ts";
import {IndustryExchangeListFilters} from "./industry-exchange-list-filters/IndustryExchangeListFilters.tsx";
import {useIndustryExchangeListStyles} from "./IndustryExchangeListStyles.tsx";

interface IndustryExchangeListProps
{
    exchanges: readonly IndustryExchangeExample[];
}

export function IndustryExchangeList({exchanges}: IndustryExchangeListProps)
{
    const styles = useIndustryExchangeListStyles();

    return (
        <aside id={"industry-demo-exchange-list"}
               className={styles.sidebar}
               aria-label="Example Exchanges">
            <IndustryExchangeListFilters/>

            <div id={"industry-demo-exchange-examples"}
                 className={styles.exchangeExamples}>
                {exchanges.map((exchange, index) => (
                    <article id={`industry-demo-exchange-example-${index}`}
                             key={exchange.title}
                             className={mergeClasses(styles.exchange, index === 0 && styles.selectedExchange)}>
                        <span id={`industry-demo-exchange-avatar-${index}`}
                              className={styles.exchangeAvatar}
                              aria-hidden="true">
                            <Person16Regular id={`industry-demo-exchange-person-icon-${index}`}/>
                        </span>
                        <span id={`industry-demo-exchange-copy-${index}`}
                              className={styles.exchangeCopy}>
                            <span id={`industry-demo-exchange-title-${index}`}
                                  className={styles.exchangeTitle}>
                                {exchange.title}
                            </span>
                            <span id={`industry-demo-exchange-summary-${index}`}
                                  className={styles.exchangeSummary}>
                                {exchange.summary}
                            </span>
                        </span>
                        <time id={`industry-demo-exchange-updated-${index}`}
                              className={styles.updated}>
                            {exchange.updated}
                        </time>
                    </article>
                ))}
            </div>

            <div id={"industry-demo-exchange-list-footer"}
                 className={styles.footer}
                 aria-hidden="true">
                ‹
            </div>
        </aside>
    );
}
