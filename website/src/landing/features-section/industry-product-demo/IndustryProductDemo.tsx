import type {IndustryExperience} from "../featureContent.ts";
import {IndustryDemoHeader} from "./IndustryDemoHeader.tsx";
import {IndustryDocumentView} from "./IndustryDocumentView.tsx";
import {IndustryExchangeList} from "./IndustryExchangeList.tsx";
import {useIndustryProductDemoStyles} from "./IndustryProductDemoStyles.tsx";

interface IndustryProductDemoProps
{
    industry: IndustryExperience;
}

export function IndustryProductDemo({industry}: IndustryProductDemoProps)
{
    const styles = useIndustryProductDemoStyles();

    return (
        <div
            id="industry-product-demo"
            className={styles.shell}
        >
            <div
                id="industry-demo-scaled-viewport"
                className={styles.scaledViewport}
            >
                <IndustryDemoHeader persona={industry.persona}/>
                <div
                    id="industry-demo-app-body"
                    className={styles.body}
                >
                    <IndustryExchangeList exchanges={industry.exchanges}/>
                    <IndustryDocumentView industry={industry}/>
                </div>
            </div>
        </div>
    );
}
