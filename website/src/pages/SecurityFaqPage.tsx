import {Button, makeStyles, Text, tokens} from "@fluentui/react-components";
import {Link} from "react-router-dom";
import {LandingHeader} from "../landing/LandingHeader.tsx";
import {WIDTH_CONTENT} from "../landing/shared.ts";

const useStyles = makeStyles({
    page: {
        minHeight: "100%",
        backgroundColor: "#f8faff",
    },

    content: {
        width: WIDTH_CONTENT,
        maxWidth: "100%",
        margin: "0 auto",
        padding: "2rem 2rem 3rem",
        boxSizing: "border-box",
        display: "flex",
        flexDirection: "column",
        gap: "1rem",
    },

    heading: {
        fontSize: tokens.fontSizeHero700,
        lineHeight: tokens.lineHeightHero700,
        margin: 0,
    },

    card: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: "0.75rem",
        padding: "1.25rem",
        display: "flex",
        flexDirection: "column",
        gap: "0.45rem",
    },

    question: {
        fontSize: tokens.fontSizeBase500,
        fontWeight: tokens.fontWeightSemibold,
        margin: 0,
    },

    answer: {
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase300,
        lineHeight: tokens.lineHeightBase300,
    },

    buttonRow: {
        marginTop: "0.5rem",
        display: "flex",
        gap: "0.75rem",
        flexWrap: "wrap",
    },
});

function FaqCard({question, answer}: { question: string; answer: string })
{
    const styles = useStyles();

    return (
        <section className={styles.card}>
            <h2 className={styles.question}>{question}</h2>
            <Text className={styles.answer}>{answer}</Text>
        </section>
    );
}

export function SecurityFaqPage()
{
    const styles = useStyles();

    return (
        <div className={styles.page}>
            <LandingHeader/>
            <main className={styles.content}>
                <h1 className={styles.heading}>Security FAQ</h1>
                <Text>
                    This FAQ explains how DocuHyphen protects customer data at a high level.
                    It is intentionally transparent for trust while not exposing operational security detail.
                </Text>

                <FaqCard
                    question="How is customer data protected?"
                    answer="Data is protected through layered controls including encryption, access controls, identity verification, and continuous monitoring."
                />
                <FaqCard
                    question="Do you support SSO and enterprise identity providers?"
                    answer="Yes. Organizations can configure Microsoft or Google identity providers, with strict validation and organization-level policy enforcement."
                />
                <FaqCard
                    question="Can you limit who can access our data?"
                    answer="Yes. Access is controlled through role-based permissions, organization boundaries, and session controls including device-based session management."
                />
                <FaqCard
                    question="Do you keep audit logs?"
                    answer="Yes. Security-sensitive actions are logged for traceability, including sign-in events, session revocations, and critical administration actions."
                />
                <FaqCard
                    question="How do you handle compromised sessions or accounts?"
                    answer="Sessions can be revoked per device or globally. Risk and policy checks are applied during refresh and request processing to block invalid access quickly."
                />
                <FaqCard
                    question="How is tenant isolation handled?"
                    answer="Organization-level scoping is enforced in authentication and authorization flows so one customer cannot access another customer's data."
                />
                <FaqCard
                    question="Do you share security details publicly?"
                    answer="We share control categories and assurances publicly, while detailed defensive configurations are restricted to reduce abuse risk."
                />
                <FaqCard
                    question="Can customers review your security posture?"
                    answer="Yes. Enterprise customers can request security documentation through our security and compliance process."
                />

                <div className={styles.buttonRow}>
                    <Button as={Link} to="/help/idp-setup" appearance="secondary" shape="circular">
                        View IdP Setup Guide
                    </Button>
                    <Button as={Link} to="/" appearance="outline" shape="circular">
                        Back to Home
                    </Button>
                </div>
            </main>
        </div>
    );
}

