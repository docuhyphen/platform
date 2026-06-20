import {Text, Title2, makeStyles, tokens} from "@fluentui/react-components";
import {ArrowRight20Regular} from "@fluentui/react-icons";
import {LinkButton} from "../shared/LinkButton.tsx";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "./shared.ts";

type Testimonial = {
    quote: string;
    author: string;
    role: string;
    company: string;
};

const testimonials: Testimonial[] = [
    {
        quote: "Our compliance team finally has the audit trail it always wanted. Onboarding new clients now takes hours instead of days.",
        author: "Lerato N.",
        role: "Head of Operations",
        company: "Mid-size accounting firm",
    },
    {
        quote: "We replaced three separate file-sharing tools with DocuHyphen. The session controls and per-document logging are unmatched.",
        author: "Daniel R.",
        role: "Partner",
        company: "Commercial law practice",
    },
    {
        quote: "Patients submit ID and consent forms securely without us standing up new infrastructure. It just works.",
        author: "Dr. Priya S.",
        role: "Clinical Director",
        company: "Multi-site healthcare group",
    },
];

const useStyles = makeStyles({
    wrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    container: {
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800,
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100",
    },

    grid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    card: {
        backgroundColor: tokens.colorNeutralBackground1,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_MD,
        boxShadow: tokens.shadow4,
    },

    quote: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase500,
        fontStyle: "italic",
    },

    attribution: {
        display: "flex",
        flexDirection: "column",
        gap: "0.15rem",
        marginTop: "auto",
    },

    author: {
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground1,
    },

    role: {
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase200,
    },

    caseStudyCard: {
        background: "linear-gradient(135deg, #20344d 0%, #385980 55%, #7ea6d6 100%)",
        color: "white",
        borderRadius: CARD_RADIUS,
        padding: SPACE_LG,
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: SPACE_MD,

        [BREAKPOINT_MOBILE]: {
            flexDirection: "column",
            alignItems: "flex-start",
        },
    },

    caseStudyText: {
        display: "flex",
        flexDirection: "column",
        gap: "0.25rem",
    },

    caseStudyTitle: {
        color: "white",
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeBase500,
    },

    caseStudyBody: {
        color: "rgba(255,255,255,0.85)",
    },
});

export function TestimonialsSection()
{
    const styles = useStyles();

    return (
        <section className={styles.wrapper}>
            <div className={styles.container}>
                <div className={styles.intro}>
                    <Title2 className={styles.sectionTitle}>What teams say</Title2>
                    <Text size={500} className={styles.subheading}>
                        Real feedback from professionals who exchange sensitive documents every day.
                    </Text>
                </div>

                <div className={styles.grid}>
                    {testimonials.map((t) => (
                        <article key={t.author} className={styles.card}>
                            <Text className={styles.quote}>&ldquo;{t.quote}&rdquo;</Text>
                            <div className={styles.attribution}>
                                <Text className={styles.author}>{t.author}</Text>
                                <Text className={styles.role}>{t.role} &middot; {t.company}</Text>
                            </div>
                        </article>
                    ))}
                </div>

                <div className={styles.caseStudyCard}>
                    <div className={styles.caseStudyText}>
                        <Text className={styles.caseStudyTitle}>
                            How a 200-person law firm cut document turnaround by 60%
                        </Text>
                        <Text className={styles.caseStudyBody}>
                            See the playbook for replacing email attachments with structured exchange sessions.
                        </Text>
                    </div>
                    <LinkButton
                        appearance="primary"
                        to="/resources"
                        shape="circular"
                        icon={<ArrowRight20Regular/>}
                        iconPosition="after"
                    >
                        Read case study
                    </LinkButton>
                </div>
            </div>
        </section>
    );
}
