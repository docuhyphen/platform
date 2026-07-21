import {Badge, Button, Divider, Text, Title2, makeStyles, mergeClasses, tokens} from "@fluentui/react-components";
import {useState} from "react";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    SPACE_SM,
    SPACE_XL,
    SPACE_XS,
    WIDTH_CONTENT, WIDTH_SUBTITLE,
} from "./shared.ts";

type RiskItem = {
    id: number;
    subtitle: string;
    title: string;
    body: string;
    imageSrc: string;
    imageAlt: string;
    imagePosition?: string;
    variantClass: string;
};

const useStyles = makeStyles({
    sectionWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        backgroundColor: "#f4f8fd",
        backgroundImage: "url('/backgrounds/leaky-file-network.svg')",
        backgroundRepeat: "no-repeat",
        backgroundSize: "cover",
        backgroundPosition: "center",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    introWrapper: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        marginBottom: SPACE_XL,
        alignItems: "flex-start",
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        alignItems: "flex-start",
        gap: SPACE_SM,

        [BREAKPOINT_MOBILE]: {
            gap: SPACE_SM,
        },
    },

    cardsWrapper: {
        boxSizing: "border-box",
        background: "transparent",
        marginTop: 0,
    },

    cardsContainer: {
        display: "grid",
        gap: SPACE_LG,
        gridTemplateColumns: "repeat(2, 1fr)",
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },

    card: {
        borderRadius: CARD_RADIUS,
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        boxShadow: tokens.shadow4,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        transitionProperty: "transform, box-shadow",
        transitionDuration: "200ms",
        transitionTimingFunction: "ease",

        ":hover": {
            transform: "translateY(-2px)",
            boxShadow: tokens.shadow8,
        },
    },

    card1: {
        backgroundColor: "rgba(242 247 251, 0.5)",
        borderTop: "0.4rem solid #5B9BD5",
    },

    card2: {
        backgroundColor: "rgba(246 244 250, 0.5)",
        borderTop: "0.4rem solid #8E7CC3",
    },

    card3: {
        backgroundColor: "rgb(241 249 247, 0.5)",
        borderTop: "0.4rem solid #4FB3A8",
    },

    card4: {
        backgroundColor: "rgba(251 247 241, 0.5)",
        borderTop: "0.4rem solid #C7A76C",
    },

    cardImage: {
        width: "100%",
        height: "11.5rem",
        objectFit: "cover",
        display: "block",
    },

    cardContent: {
        padding: SPACE_LG,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
    },

    cardSubtitle: {
        display: "flex",
        alignItems: "center",
        gap: SPACE_XS,
        color: tokens.colorNeutralForeground2,
        fontSize: tokens.fontSizeBase400,
        lineHeight: tokens.lineHeightBase400,
        fontWeight: tokens.fontWeightSemibold,
    },

    cardTitle: {
        display: "flex",
        minHeight: "2.5em",
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
    },

    readMoreButton: {
        alignSelf: "flex-start",
        padding: 0,
        minWidth: "unset",
        color: tokens.colorBrandForeground1,
    },

    cardParagraph: {
        marginTop: SPACE_XS,
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100"
    },
});

export function RisksSection()
{
    const styles = useStyles();
    const [expandedCards, setExpandedCards] = useState<Record<number, boolean>>({});

    const toggleCard = (riskId: number) =>
    {
        setExpandedCards((prev) => ({
            ...prev,
            [riskId]: !prev[riskId],
        }));
    };

    const risks: RiskItem[] = [
        {
            id: 1,
            subtitle: "High risk of unauthorized access",
            title: "Confidential Files Can Be Accessed by the Wrong People",
            body: "Sensitive documents can be intercepted, forwarded without permission, downloaded to unsecured devices, or stored indefinitely in inboxes and shared drives. Once sent, control is lost.",
            imageSrc: "/demo-screenshots/app-screenshot-banking-lending.JPG",
            imageAlt: "Access control and session overview screenshot",
            imagePosition: "center 18%",
            variantClass: styles.card1,
        },
        {
            id: 2,
            subtitle: "Uncontrolled document distribution",
            title: "Shared Documents Spread Beyond Intended Recipients",
            body: "Links can be forwarded. Emails can be mistyped. Attachments can be duplicated and redistributed. There is no reliable way to verify the intended recipient or restrict downstream sharing.",
            imageSrc: "/demo-screenshots/app-screenshot-legal.JPG",
            imageAlt: "Sharing workflow screenshot showing controlled recipients",
            imagePosition: "center 20%",
            variantClass: styles.card2,
        },
        {
            id: 3,
            subtitle: "No defensible audit trail",
            title: "Without Activity Logs, You Cannot Prove What Happened",
            body: "Most tools provide little visibility into who accessed documents, when they were accessed, or what actions were taken. When disputes or investigations arise, proof is often unavailable.",
            imageSrc: "/demo-screenshots/app-screenshot-accounting.JPG",
            imageAlt: "Audit and tracking related screenshot",
            imagePosition: "center 16%",
            variantClass: styles.card3,
        },
        {
            id: 4,
            subtitle: "Regulatory exposure (POPIA, GDPR & similar laws)",
            title: "Weak Controls Increase Compliance and Legal Risk",
            body: "Data protection regulations require secure processing, controlled access, and demonstrable accountability. Without encryption, access control, and detailed logging, organizations risk fines, legal exposure, and reputational damage.",
            imageSrc: "/demo-screenshots/app-screenshot-healthcare.JPG",
            imageAlt: "Compliance-focused secure sharing screenshot",
            imagePosition: "center 24%",
            variantClass: styles.card4,
        },
    ];

    return (
        <>
            <section className={styles.sectionWrapper}>
                <div className={styles.introWrapper}>
                    <div className={styles.intro}>
                        <Title2 align="start" className={styles.sectionTitle}>
                            Did you know?
                        </Title2>
                        <Text size={500} className={styles.subheading}>
                            The hidden risks of everyday document sharing
                        </Text>
                    </div>
                </div>

                <section className={styles.cardsWrapper}>
                    <div className={styles.cardsContainer}>
                        {risks.map((risk) =>
                        {
                            const isExpanded = !!expandedCards[risk.id];

                            return (
                                <div key={risk.id} className={mergeClasses(styles.card, risk.variantClass)}>

                                    <div className={styles.cardContent}>
                                        <Text className={styles.cardSubtitle} weight="semibold">
                                            <Badge size="large" appearance="filled">{risk.id}</Badge>
                                            {risk.subtitle}
                                        </Text>
                                        <Text weight="bold" className={styles.cardTitle}>{risk.title}</Text>

                                        <Button
                                            appearance="transparent"
                                            className={styles.readMoreButton}
                                            onClick={() => toggleCard(risk.id)}
                                        >
                                            {isExpanded ? "Read less" : "Read more"}
                                        </Button>

                                        {isExpanded && <Text className={styles.cardParagraph}>{risk.body}</Text>}
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </section>

            </section>
            <Divider/>
        </>
    );
}

