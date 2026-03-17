import {Badge, Card, CardHeader, Text, Title2, makeStyles, tokens} from "@fluentui/react-components";
import {
    BREAKPOINT_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_MD,
    WIDTH_CONTENT,
} from "./shared.ts";
import {IndustryCarouselSection} from "./IndustryCarouselSection.tsx";

type FeatureItem = {
    title: string;
    body: string;
};

const useStyles = makeStyles({
    introWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
    },

    cardsWrapper: {
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    cards: {
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: SPACE_LG,

        [BREAKPOINT_MOBILE]: {
            gridTemplateColumns: "1fr",
            gap: SPACE_MD,
        },
    },
});

export function FeaturesSection()
{
    const styles = useStyles();

    const features: FeatureItem[] = [
        {
            title: "Structured Document Requests",
            body: "Create controlled share sessions to request documents from customers or partners, with defined access, expiry controls, and full visibility.",
        },
        {
            title: "Secure Document Delivery",
            body: "Deliver financial, legal, and confidential documents without email attachments or public links, ensuring only authorized recipients can access them.",
        },
        // {
        //     title: "Encryption by Default",
        //     body: "Files are encrypted during upload, storage, and download, with optional end-to-end encryption for maximum confidentiality.",
        // },
        {
            title: "Full Audit & Compliance Logging",
            body: "Every upload, view, download, and action is recorded, giving you defensible audit trails and supporting regulatory compliance.",
        },
    ];

    return (
        <>
            <section className={styles.introWrapper}>
                <section className={styles.intro}>
                    <Title2 className={styles.sectionTitle}>
                        Secure, Controlled &amp; Compliant Document Exchange
                    </Title2>
                    <Text size={500}>
                        A purpose-built platform designed specifically for exchanging
                        sensitive business and customer documents, securely, transparently,
                        and compliantly.
                    </Text>
                </section>
            </section>
            <IndustryCarouselSection/>

            <section className={styles.cardsWrapper}>
                <div className={styles.cards}>
                    {features.map((feature) => (
                        <Card key={feature.title} appearance="filled-alternative">
                            <CardHeader
                                header={
                                    <Badge size="extra-large" appearance="tint">
                                        <Text weight="bold">{feature.title}</Text>
                                    </Badge>
                                }
                            />
                            <Text>{feature.body}</Text>
                        </Card>
                    ))}
                </div>
            </section>
        </>
    );
}

