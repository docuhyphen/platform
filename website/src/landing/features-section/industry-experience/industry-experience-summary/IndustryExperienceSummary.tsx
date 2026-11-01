import {Button, Text} from "@fluentui/react-components";
import {Open20Regular} from "@fluentui/react-icons";
import type {IndustryExperience} from "../../featureContent.ts";
import {useIndustryExperienceSummaryStyles} from "./IndustryExperienceSummaryStyles.tsx";

interface IndustryExperienceSummaryProps
{
    industry: IndustryExperience;
}

export function IndustryExperienceSummary({industry}: IndustryExperienceSummaryProps)
{
    const styles = useIndustryExperienceSummaryStyles();

    return (
        <div id={"industry-product-experience-summary"}
             className={styles.summary}>
            <div id={"industry-product-experience-copy"}
                 className={styles.copy}>
                <Text id={"industry-product-experience-name"}
                      className={styles.industryName}>
                    {industry.title}
                </Text>
                <Text id={"industry-product-experience-outcome"}
                      className={styles.outcome}>
                    {industry.outcome}
                </Text>
            </div>
            <Button id={"industry-product-experience-action"}
                    appearance="primary"
                    as="a"
                    size={"small"}
                    className={styles.action}
                    href={`/industries/${industry.slug}`}
                    icon={<Open20Regular id={"industry-product-experience-action-icon"}
                                         aria-hidden="true"/>}
                    iconPosition="after"
                    shape="circular">
                Explore this solution
            </Button>
        </div>
    );
}
