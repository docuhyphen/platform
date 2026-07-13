import {Text} from "@fluentui/react-components";
import {ArrowRight20Regular} from "@fluentui/react-icons";
import {LinkButton} from "../shared/LinkButton.tsx";
import {useIndustriesPageStyles} from "./IndustriesPageStyles.tsx";

export function IndustriesPageFinalCta()
{
    const styles = useIndustriesPageStyles();

    return (
        <section
            id="industry-detail-final-cta"
            className={styles.finalCta}
        >
            <Text size={500}>Curious how this fits your team?</Text>
            <LinkButton
                to="/pricing"
                appearance="primary"
                shape="circular"
                icon={<ArrowRight20Regular/>}
                iconPosition="after"
            >
                See pricing
            </LinkButton>
            <LinkButton
                to="/security"
                appearance="secondary"
                shape="circular"
            >
                Read about security
            </LinkButton>
        </section>
    );
}
