import {Text, Title2, Title3} from "@fluentui/react-components";
import {History24Regular, PeopleTeam24Regular, ShieldLock24Regular} from "@fluentui/react-icons";
import {useAboutPageStyles} from "./AboutPageStyles.tsx";

export function AboutPrinciples()
{
    const styles = useAboutPageStyles();

    return (
        <section
            id="about-principles"
            className={styles.section}
        >
            <div
                id="about-principles-heading"
                className={styles.sectionHeading}
            >
                <Title2
                    id="about-principles-title"
                    className={styles.sectionTitle}
                >
                    Built for work that needs proof
                </Title2>
                <Text
                    id="about-principles-description"
                    className={styles.sectionIntro}
                    size={500}
                >
                    Three principles guide every product decision.
                </Text>
            </div>

            <div
                id="about-principle-grid"
                className={styles.principleGrid}
            >
                <article
                    id="about-principle-operator-led"
                    className={styles.principleCard}
                >
                    <span
                        id="about-principle-operator-led-icon"
                        className={styles.iconWrap}
                    >
                        <PeopleTeam24Regular aria-hidden="true"/>
                    </span>
                    <Title3
                        id="about-principle-operator-led-title"
                        className={styles.principleTitle}
                    >
                        Operator-led
                    </Title3>
                    <Text
                        id="about-principle-operator-led-text"
                        className={styles.principleText}
                    >
                        Grounded in real compliance, procurement, and security reviews.
                    </Text>
                </article>
                <article
                    id="about-principle-audit-first"
                    className={styles.principleCard}
                >
                    <span
                        id="about-principle-audit-first-icon"
                        className={styles.iconWrap}
                    >
                        <History24Regular aria-hidden="true"/>
                    </span>
                    <Title3
                        id="about-principle-audit-first-title"
                        className={styles.principleTitle}
                    >
                        Audit-first
                    </Title3>
                    <Text
                        id="about-principle-audit-first-text"
                        className={styles.principleText}
                    >
                        Every action should be clear, traceable, and defensible.
                    </Text>
                </article>
                <article
                    id="about-principle-independent"
                    className={styles.principleCard}
                >
                    <span
                        id="about-principle-independent-icon"
                        className={styles.iconWrap}
                    >
                        <ShieldLock24Regular aria-hidden="true"/>
                    </span>
                    <Title3
                        id="about-principle-independent-title"
                        className={styles.principleTitle}
                    >
                        Independent
                    </Title3>
                    <Text
                        id="about-principle-independent-text"
                        className={styles.principleText}
                    >
                        Your documents and usage data are never products for us to monetize.
                    </Text>
                </article>
            </div>
        </section>
    );
}
