import {Text} from "@fluentui/react-components";
import {ArrowRight20Regular} from "@fluentui/react-icons";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {useIndustriesPageStyles} from "./IndustriesPageStyles.tsx";

export function IndustriesPageFinalCta()
{
    const styles = useIndustriesPageStyles();

    return (
        <section id={"industry-detail-final-cta"}
                 className={styles.finalCta}>
            <Text id={"industry-detail-final-cta-text"}
                  size={500}>Curious how this fits your team?</Text>
            <LinkButton id={"industry-detail-final-cta-pricing"}
                        to="/pricing"
                        appearance="primary"
                        icon={<ArrowRight20Regular id={"industry-detail-final-cta-pricing-icon"}/>}
                        iconPosition="after">
                See pricing
            </LinkButton>
            <LinkButton id={"industry-detail-final-cta-security"}
                        to="/security"
                        appearance="secondary">
                Read about security
            </LinkButton>
        </section>
    );
}
