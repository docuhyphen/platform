import {Text} from "@fluentui/react-components";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

type PricingFeatureValueProps = {
    id: string;
    value: string;
};

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

