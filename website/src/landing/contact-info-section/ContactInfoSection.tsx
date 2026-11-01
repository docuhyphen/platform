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
                <div id={"contact-info-section-div-1"}
                     className={styles.introWrapper}>
                    <div id={"contact-info-section-div-2"}
                         className={styles.intro}>
                        <Title2
                            id="home-contact-info-title"
                            align="start"
                            className={styles.sectionTitle}
                        >
                            Get in touch
                        </Title2>
                        <Text id={"contact-info-section-text-1"}
                              size={500}
                              className={styles.subheading}>
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
                            <div id={"contact-info-section-div-3"}
                                 className={styles.cardHeader}>
                                <div id={"contact-info-section-div-4"}
                                     className={styles.icon}><Mail24Regular id={"contact-info-section-mail24-regular-1"}/></div>
                                <Text id={"contact-info-section-text-2"}
                                      as="h3"
                                      className={styles.cardTitle}>Sales</Text>
                            </div>
                            <div id={"contact-info-section-div-5"}
                                 className={styles.cardContent}>
                                <Text id={"contact-info-section-text-3"}>For pricing, procurement, and security reviews.</Text>
                                <a
                                    id={"contact-info-section-a-1"}
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
                            <div id={"contact-info-section-div-6"}
                                 className={styles.cardHeader}>
                                <div id={"contact-info-section-div-7"}
                                     className={styles.icon}><ChatHelp24Regular id={"contact-info-section-chat-help24-regular-1"}/></div>
                                <Text id={"contact-info-section-text-4"}
                                      as="h3"
                                      className={styles.cardTitle}>Support</Text>
                            </div>
                            <div id={"contact-info-section-div-8"}
                                 className={styles.cardContent}>
                                <Text id={"contact-info-section-text-5"}>For help with your account or technical issues.</Text>
                                <a
                                    id={"contact-info-section-a-2"}
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
                            <div id={"contact-info-section-div-9"}
                                 className={styles.cardHeader}>
                                <div id={"contact-info-section-div-10"}
                                     className={styles.icon}><Location24Regular id={"contact-info-section-location24-regular-1"}/></div>
                                <Text id={"contact-info-section-text-6"}
                                      as="h3"
                                      className={styles.cardTitle}>Office</Text>
                            </div>
                            <div id={"contact-info-section-div-11"}
                                 className={styles.cardContent}>
                                <Text id={"contact-info-section-text-7"}>
                                    We are based in South Africa.<br id={"contact-info-section-br-1"}/>
                                    Available for meetings across SAST hours.
                                </Text>
                            </div>
                        </article>
                    </div>

                    <GuidedDemoPanel idPrefix="home-contact-info"/>
                </div>
            </section>
            <Divider id={"contact-info-section-divider-1"}/>
        </>
    );
}



