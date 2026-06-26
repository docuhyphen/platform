import {Accordion, AccordionHeader, AccordionItem, AccordionPanel, makeStyles, Text, Title1, tokens} from "@fluentui/react-components";
import {LinkButton} from "../shared/LinkButton.tsx";
import {PageShell} from "../shared/PageShell.tsx";
import {Breadcrumbs} from "../shared/Breadcrumbs.tsx";

const useStyles = makeStyles({
    hero: {
        display: "flex",
        flexDirection: "column",
        gap: "0.5rem",
        textAlign: "center",
        alignItems: "center",
    },

    heroEyebrow: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },

    heroTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    heroBlurb: {
        maxWidth: "42rem",
    },

    answer: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
    },

    faqWrap: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "0.75rem",
        padding: "0.25rem",
    },

    buttonRow: {
        marginTop: "0.5rem",
        display: "flex",
        gap: "0.75rem",
        flexWrap: "wrap",
    },
});

const faqs = [
    {
        question: "How is customer data protected?",
        answer: "Data is protected through layered controls including encryption, access controls, identity verification, and continuous monitoring.",
    },
    {
        question: "Do you support SSO and enterprise identity providers?",
        answer: "Yes. Organizations can configure Microsoft or Google identity providers, with strict validation and organization-level policy enforcement.",
    },
    {
        question: "Can you limit who can access our data?",
        answer: "Yes. Access is controlled through role-based permissions, organization boundaries, and session controls including device-based session management.",
    },
    {
        question: "Do you keep audit logs?",
        answer: "Yes. Security-sensitive actions are logged for traceability, including sign-in events, session revocations, and critical administration actions.",
    },
    {
        question: "How do you handle compromised sessions or accounts?",
        answer: "Sessions can be revoked per device or globally. Risk and policy checks are applied during refresh and request processing to block invalid access quickly.",
    },
    {
        question: "How is tenant isolation handled?",
        answer: "Organization-level scoping is enforced in authentication and authorization flows so one customer cannot access another customer's data.",
    },
    {
        question: "Do you share security details publicly?",
        answer: "We share control categories and assurances publicly, while detailed defensive configurations are restricted to reduce abuse risk.",
    },
    {
        question: "Can customers review your security posture?",
        answer: "Yes. Enterprise customers can request security documentation through our security and compliance process.",
    },
];

export function SecurityFaqPage()
{
    const styles = useStyles();

    return (
        <PageShell>
            <Breadcrumbs trail={[
                {label: "Resources", to: "/resources"},
                {label: "Security FAQ"},
            ]}/>
            <section className={styles.hero}>
                <Title1 className={styles.heroTitle}>Security FAQ</Title1>
                <Text size={500} className={styles.heroBlurb} align={"center"}>
                    This FAQ explains how DocuHyphen protects customer data at a high level.
                    It is intentionally transparent for trust while not exposing operational security detail.
                </Text>
            </section>

            <div className={styles.faqWrap}>
                <Accordion collapsible multiple>
                    {faqs.map((faq, index) => (
                        <AccordionItem key={faq.question} value={String(index)}>
                            <AccordionHeader>{faq.question}</AccordionHeader>
                            <AccordionPanel>
                                <Text className={styles.answer}>{faq.answer}</Text>
                            </AccordionPanel>
                        </AccordionItem>
                    ))}
                </Accordion>
            </div>

            <div className={styles.buttonRow}>
                <LinkButton to="/resources" appearance="secondary" shape="circular">
                    Back to Resources
                </LinkButton>
            </div>
        </PageShell>
    );
}
