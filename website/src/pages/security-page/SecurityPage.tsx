import {Text, Title1, Title3} from "@fluentui/react-components";
import {
    ClipboardTaskListLtr24Regular,
    Globe24Regular,
    LockClosed24Regular,
    Person24Regular,
    Server24Regular,
    ShieldCheckmark24Regular,
} from "@fluentui/react-icons";
import type {ReactNode} from "react";
import {Breadcrumbs} from "../../shared/Breadcrumbs.tsx";
import {LinkButton} from "../../shared/LinkButton.tsx";
import {PageShell} from "../../shared/PageShell.tsx";
import {useSecurityPageStyles} from "./SecurityPageStyles.tsx";

type SecurityPillar = {
    icon: ReactNode;
    title: string;
    body: string;
};

const securityPillars: SecurityPillar[] = [
    {
        icon: <LockClosed24Regular aria-hidden="true"/>,
        title: "Protected documents",
        body: "Connections and stored documents are protected with encryption controls throughout the Exchange lifecycle.",
    },
    {
        icon: <Server24Regular aria-hidden="true"/>,
        title: "Controlled infrastructure",
        body: "The platform uses managed cloud infrastructure, protected storage, backups, and monitored application services.",
    },
    {
        icon: <Person24Regular aria-hidden="true"/>,
        title: "Identity and access",
        body: "Organization boundaries, roles, and session controls restrict who can reach an Exchange and its documents.",
    },
    {
        icon: <ClipboardTaskListLtr24Regular aria-hidden="true"/>,
        title: "Traceable activity",
        body: "Relevant document and administration activity is recorded so teams can review what happened and when.",
    },
    {
        icon: <ShieldCheckmark24Regular aria-hidden="true"/>,
        title: "Workflow controls",
        body: "Configurable review and approval steps help teams apply consistent controls to sensitive document work.",
    },
    {
        icon: <Globe24Regular aria-hidden="true"/>,
        title: "Tenant isolation",
        body: "Authentication and authorization checks keep organization data scoped to the appropriate principals.",
    },
];

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
                        <Title3 as="h2">{pillar.title}</Title3>
                        <Text>{pillar.body}</Text>
                    </section>
                ))}
            </div>

            <section
                id="security-expectations"
                className={styles.band}
            >
                <Title3 as="h2">Controls teams can use</Title3>
                <ul className={styles.list}>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Role-based access</Text>
                        <Text>Give participants only the access needed for their part of an Exchange.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Revocable sharing</Text>
                        <Text>Manage access as participants, responsibilities, and document needs change.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Document history</Text>
                        <Text>Review activity associated with documents and Exchange progress.</Text>
                    </li>
                    <li className={styles.listItem}>
                        <Text weight="semibold">Structured approvals</Text>
                        <Text>Use workflows to guide review, decisions, and completion.</Text>
                    </li>
                </ul>
            </section>

            <section
                id="security-contact-actions"
                className={styles.cta}
            >
                <LinkButton
                    id="security-contact-button"
                    to="/contact"
                    appearance="primary"
                    shape="circular"
                >
                    Discuss your security requirements
                </LinkButton>
            </section>
        </PageShell>
    );
}

