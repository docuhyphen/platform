import {Text} from "@fluentui/react-components";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

type PricingFeatureValueProps = {
    id: string;
    value: string;
};

/**
 * Renders one cell of the plan comparison table.
 *
 * A definite answer is emphasised so the table can be scanned quickly. A capability that is
 * planned but not sold yet is rendered quietly instead, so a reader can never mistake it for
 * something the plan entitles them to today.
 */
export function PricingFeatureValue({id, value}: PricingFeatureValueProps)
{
    const styles = usePricingPageStyles();

    if (value === "Coming soon")
    {
        return (
            <Text id={id}
                  className={styles.pendingValue}>
                {value}
            </Text>
        );
    }

    if (value === "Included" || value === "Not included")
    {
        return <strong id={id}>{value}</strong>;
    }

    return <span id={id}>{value}</span>;
}

