import {Button, Text, Title1, Title3} from "@fluentui/react-components";
import {ArrowRight20Regular, CalendarClock24Regular, DocumentSearch24Regular, PersonCall24Regular} from "@fluentui/react-icons";
import {Link} from "react-router-dom";
import {PageShell} from "../shared/PageShell.tsx";
import {usePricingPageStyles} from "./PricingPageStyles.tsx";

export function PricingPage()
{
    const styles = usePricingPageStyles();

    return (
        <PageShell>
            <div
                id="pricing-page"
                className={styles.page}
            >
                <section className={styles.hero} aria-labelledby="pricing-page-title">
                    <div className={styles.heroIcon} aria-hidden="true">
                        <CalendarClock24Regular/>
                    </div>
                    <Text className={styles.eyebrow}>Pricing is in progress</Text>
                    <Title1
                        id="pricing-page-title"
                        as="h1"
                        className={styles.title}
                    >
                        We are shaping plans that fit how your team works.
                    </Title1>
                    <Text size={500} className={styles.description}>
                        Our pricing is not ready to publish just yet. While we finish it, discover how DocuHyphen can bring your document exchanges, access controls, and workflows into one secure place.
                    </Text>
                    <div className={styles.actions}>
                        <Button
                            id="pricing-explore-features"
                            as="a"
                            href="/#features"
                            appearance="primary"
                            shape="circular"
                            icon={<ArrowRight20Regular/>}
                            iconPosition="after"
                        >
                            Explore features
                        </Button>
                        <Button
                            id="pricing-contact-team"
                            as="a"
                            href="/contact"
                            appearance="secondary"
                            shape="circular"
                        >
                            Contact our team
                        </Button>
                    </div>
                </section>

                <section className={styles.nextSteps} aria-labelledby="pricing-next-steps-title">
                    <Text id="pricing-next-steps-title" className={styles.nextStepsLabel}>In the meantime</Text>
                    <div className={styles.cardGrid}>
                        <article className={styles.card}>
                            <div className={styles.cardIcon} aria-hidden="true"><DocumentSearch24Regular/></div>
                            <Title3>See what DocuHyphen can do</Title3>
                            <Text>Explore the tools that help your team manage secure, structured document exchanges.</Text>
                            <Link id="pricing-features-link" to="/#features" className={styles.cardLink}>
                                View features <ArrowRight20Regular/>
                            </Link>
                        </article>
                        <article className={styles.card}>
                            <div className={styles.cardIcon} aria-hidden="true"><PersonCall24Regular/></div>
                            <Title3>Talk through your needs</Title3>
                            <Text>Tell us about your process, team, and requirements. We will help you find the right path forward.</Text>
                            <Link id="pricing-contact-link" to="/contact" className={styles.cardLink}>
                                Get in touch <ArrowRight20Regular/>
                            </Link>
                        </article>
                    </div>
                </section>
            </div>
        </PageShell>
    );
}
