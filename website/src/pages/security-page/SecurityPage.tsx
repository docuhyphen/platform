import {Text, Title1, Title3} from "@fluentui/react-components";
import {Breadcrumbs} from "../../shared/breadcrumbs/Breadcrumbs.tsx";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {PageShell} from "../../shared/page-shell/PageShell.tsx";
import {useSecurityPageStyles} from "./SecurityPageStyles.tsx";
import {securityPillars} from "./securityPillars.tsx";

export function SecurityPage()
{
    const styles = useSecurityPageStyles();

    return (
        <PageShell>
            <Breadcrumbs trail={[{label: "Home", to: "/"}, {label: "Security and trust"}]}/>
            <section
                id="security-page-hero"
                className={styles.hero}
            >
                <Title1
                    id="security-page-title"
                    as="h1"
                    className={styles.heroTitle}
                >
                    Security is part of every Exchange.
                </Title1>
                <Text
                    id="security-page-description"
                    size={500}
                    className={styles.heroBlurb}
                    align="center"
                >
                    DocuHyphen helps teams keep sensitive documents in a controlled workspace with clear access,
                    workflow, and activity records.
                </Text>
            </section>

            <div
                id="security-controls-grid"
                className={styles.grid}
            >
                {securityPillars.map((pillar, index) => (
                    <section
                        id={`security-control-${index}`}
                        key={pillar.title}
                        className={styles.pillar}
                    >
                        <div
                            id={`security-control-icon-${index}`}
                            className={styles.pillarIcon}
                        >
                            {pillar.icon}
                        </div>
                        <Title3 id={`security-control-title-${index}`}
                                as="h2">{pillar.title}</Title3>
                        <Text id={`security-control-body-${index}`}>{pillar.body}</Text>
                    </section>
                ))}
            </div>

            <section
                id="security-expectations"
                className={styles.band}
            >
                <Title3 id={"security-page-title3-2"}
                        as="h2">Controls teams can use</Title3>
                <ul id={"security-page-ul-1"}
                    className={styles.list}>
                    <li id={"security-page-li-1"}
                        className={styles.listItem}>
                        <Text id={"security-page-text-2"}
                              weight="semibold">Role-based access</Text>
                        <Text id={"security-page-text-3"}>Give participants only the access needed for their part of an Exchange.</Text>
                    </li>
                    <li id={"security-page-li-2"}
                        className={styles.listItem}>
                        <Text id={"security-page-text-4"}
                              weight="semibold">Revocable sharing</Text>
                        <Text id={"security-page-text-5"}>Manage access as participants, responsibilities, and document needs change.</Text>
                    </li>
                    <li id={"security-page-li-3"}
                        className={styles.listItem}>
                        <Text id={"security-page-text-6"}
                              weight="semibold">Document history</Text>
                        <Text id={"security-page-text-7"}>Review activity associated with documents and Exchange progress.</Text>
                    </li>
                    <li id={"security-page-li-4"}
                        className={styles.listItem}>
                        <Text id={"security-page-text-8"}
                              weight="semibold">Structured approvals</Text>
                        <Text id={"security-page-text-9"}>Use workflows to guide review, decisions, and completion.</Text>
                    </li>
                </ul>
            </section>

            <section
                id="security-contact-actions"
                className={styles.cta}
            >
                <LinkButton
                    id="security-contact-button"
                    to="/#home-contact-info"
                    appearance="primary"
                >
                    Discuss your security requirements
                </LinkButton>
            </section>
        </PageShell>
    );
}

