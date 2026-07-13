import {Text, Title1} from "@fluentui/react-components";
import {PageShell} from "../../shared/PageShell.tsx";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {AboutCommitments} from "./AboutCommitments.tsx";
import {AboutExchangeVisual} from "./AboutExchangeVisual.tsx";
import {AboutPrinciples} from "./AboutPrinciples.tsx";
import {useAboutPageStyles} from "./AboutPageStyles.tsx";

export function AboutPage()
{
    const styles = useAboutPageStyles();

    return (
        <PageShell>
            <section
                id="about-hero"
                className={styles.hero}
            >
                <div
                    id="about-hero-copy"
                    className={styles.heroCopy}
                >
                    <Text
                        id="about-hero-eyebrow"
                        className={styles.heroEyebrow}
                    >
                        About DocuHyphen
                    </Text>
                    <Title1
                        id="about-hero-title"
                        className={styles.heroTitle}
                    >
                        Trust belongs in every Exchange.
                    </Title1>
                    <Text
                        id="about-hero-description"
                        className={styles.heroText}
                        size={500}
                    >
                        DocuHyphen gives regulated teams one secure place to coordinate documents, people,
                        approvals, and proof.
                    </Text>
                </div>
                <AboutExchangeVisual/>
            </section>

            <AboutPrinciples/>
            <AboutCommitments/>

            <div
                id="about-actions"
                className={styles.cta}
            >
                <LinkButton
                    id="about-contact-button"
                    to="/contact"
                    appearance="primary"
                    shape="circular"
                >
                    Get in touch
                </LinkButton>
                <LinkButton
                    id="about-security-button"
                    to="/security"
                    appearance="secondary"
                    shape="circular"
                >
                    Read about our security
                </LinkButton>
            </div>
        </PageShell>
    );
}
