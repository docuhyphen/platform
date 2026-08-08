import {Divider, Text, Title2} from "@fluentui/react-components";
import {Mail24Regular, ChatHelp24Regular, Location24Regular} from "@fluentui/react-icons";
import {SALES_EMAIL_URL} from "../shared.ts";
import {GuidedDemoPanel} from "../guided-demo-panel/GuidedDemoPanel.tsx";
import {useContactInfoSectionStyles} from "./ContactInfoSectionStyles.tsx";

export function ContactInfoSection()
{
    const styles = useContactInfoSectionStyles();

    return (
        <>
            <section
                id="home-contact-info"
                className={styles.wrapper}
                aria-labelledby="home-contact-info-title"
            >
                <div className={styles.introWrapper}>
                    <div className={styles.intro}>
                        <Title2
                            id="home-contact-info-title"
                            align="start"
                            className={styles.sectionTitle}
                        >
                            Get in touch
                        </Title2>
                        <Text size={500} className={styles.subheading}>
                            Whether you&apos;re evaluating, mid-rollout, or already a customer, we&apos;re here to help.
                        </Text>
                    </div>
                </div>

                <div
                    id="home-contact-info-grid"
                    className={styles.grid}
                >
                    <div
                        id="home-contact-info-cards"
                        className={styles.cardsColumn}
                    >
                        <article
                            id="home-contact-info-sales"
                            className={styles.card}
                        >
                            <div className={styles.cardHeader}>
                                <div className={styles.icon}><Mail24Regular/></div>
                                <Text as="h3" className={styles.cardTitle}>Sales</Text>
                            </div>
                            <div className={styles.cardContent}>
                                <Text>For pricing, procurement, and security reviews.</Text>
                                <a
                                    className={styles.link}
                                    href={SALES_EMAIL_URL}
                                >
                                    sales@docuhyphen.com
                                </a>
                            </div>
                        </article>

                        <article
                            id="home-contact-info-support"
                            className={styles.card}
                        >
                            <div className={styles.cardHeader}>
                                <div className={styles.icon}><ChatHelp24Regular/></div>
                                <Text as="h3" className={styles.cardTitle}>Support</Text>
                            </div>
                            <div className={styles.cardContent}>
                                <Text>For help with your account or technical issues.</Text>
                                <a
                                    className={styles.link}
                                    href="mailto:support@docuhyphen.com"
                                >
                                    support@docuhyphen.com
                                </a>
                            </div>
                        </article>

                        <article
                            id="home-contact-info-office"
                            className={styles.card}
                        >
                            <div className={styles.cardHeader}>
                                <div className={styles.icon}><Location24Regular/></div>
                                <Text as="h3" className={styles.cardTitle}>Office</Text>
                            </div>
                            <div className={styles.cardContent}>
                                <Text>
                                    We are based in South Africa.<br/>
                                    Available for meetings across SAST hours.
                                </Text>
                            </div>
                        </article>
                    </div>

                    <GuidedDemoPanel idPrefix="home-contact-info"/>
                </div>
            </section>
            <Divider/>
        </>
    );
}



