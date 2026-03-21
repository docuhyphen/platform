import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Text,
    Title2,
    makeStyles,
    tokens,
} from "@fluentui/react-components";
import {Add20Filled, Subtract20Filled} from "@fluentui/react-icons";
import {useState} from "react";
import {
    BREAKPOINT_MOBILE,
    CARD_RADIUS,
    LAW_SECTION_GRADIENT,
    SECTION6_RADIUS_DESKTOP,
    SECTION6_RADIUS_MOBILE,
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_SM,
    SPACE_XS,
    WIDTH_CONTENT,
    WIDTH_SUBTITLE,
} from "./shared.ts";

type AudienceItem = {
    title: string;
    body: string;
};

type AudienceGroup = {
    title: string;
    items: AudienceItem[];
};

const useStyles = makeStyles({
    wrapper: {
        backgroundColor: "transparent",
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        maxWidth: WIDTH_CONTENT,
        margin: "3rem auto",
        marginBottom: SPACE_LG,
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorBrandForeground1,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800
    },

    container: {
        boxSizing: "border-box",
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        width: "100%",
        color: "white",
        background: LAW_SECTION_GRADIENT,
        borderRadius: SECTION6_RADIUS_DESKTOP,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        padding: SECTION_PADDING_DESKTOP,

        [BREAKPOINT_MOBILE]: {
            borderRadius: SECTION6_RADIUS_MOBILE,
            padding: SECTION_PADDING_MOBILE,
            margin: "0",
        },
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForeground1,
        fontWeight: "100"
    },

    accordionHeader: {
        color: "white",
        fontSize: tokens.fontSizeBase500,
        lineHeight: tokens.lineHeightBase500,
        fontWeight: tokens.fontWeightSemibold,
        marginTop: "1.3rem"
    },

    accordionPanel: {
        color: "white",
    },

    list: {
        border: "1px solid white",
        borderRadius: CARD_RADIUS,
        padding: "12px",
        marginTop: SPACE_XS,
        paddingLeft: SPACE_SM,
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,

        "& li": {
            display: "flex",
            flexDirection: "column",
            gap: "0.25rem",
        },
    },
});

export function AudienceSection()
{
    const styles = useStyles();
    const [openItem, setOpenItem] = useState<number>(0);

    const audienceGroups: AudienceGroup[] = [
        {
            title: "Financial & Professional Services",
            items: [
                {
                    title: "Accounting & audit firms",
                    body: "Secure exchange of tax records, financial statements, and supporting documents.",
                },
                {
                    title: "Financial advisors & wealth managers",
                    body: "Protect client identity and financial data while maintaining full audit trails.",
                },
                {
                    title: "Banks & lending institutions",
                    body: "Collect and share KYC, income verification, and compliance documents securely.",
                },
            ],
        },
        {
            title: "Insurance & Risk Management",
            items: [
                {
                    title: "Insurance companies",
                    body: "Secure handling of policy documents, claims, and customer identity records.",
                },
                {
                    title: "Insurance brokers & underwriters",
                    body: "Controlled document sharing between clients, insurers, and assessors.",
                },
                {
                    title: "Claims management firms",
                    body: "Track document submissions with visibility and accountability.",
                },
            ],
        },
        {
            title: "Legal & Regulatory",
            items: [
                {
                    title: "Law firms & legal practices",
                    body: "Exchange contracts, affidavits, and sensitive legal documents with clients.",
                },
                {
                    title: "Compliance & risk consultancies",
                    body: "Maintain verifiable audit trails for regulated workflows.",
                },
                {
                    title: "Corporate secretarial services",
                    body: "Secure storage and exchange of statutory and company records.",
                },
            ],
        },
        {
            title: "Corporate & Enterprise Operations",
            items: [
                {
                    title: "Medium to large enterprises",
                    body: "Internal and external document sharing with verified parties.",
                },
                {
                    title: "Procurement & vendor onboarding teams",
                    body: "Collect compliance documents from suppliers securely.",
                },
                {
                    title: "HR & payroll departments",
                    body: "Exchange employee identity, tax, and contract documents safely.",
                },
            ],
        },
    ];

    return (
        <section className={styles.wrapper}>
            <section className={styles.intro}>
                <Title2 className={styles.sectionTitle}>
                    Industry Coverage
                </Title2>
                <Text className={styles.subheading} size={500}>
                    Check out some use cases for your industry
                </Text>
            </section>

            <section className={styles.container}>
                <Accordion
                    collapsible
                    openItems={openItem ? [openItem] : []}
                    onToggle={(_, data) => {
                        const nextOpenItem = data.openItems[0];
                        setOpenItem(typeof nextOpenItem === "number" ? nextOpenItem : 0);
                    }}
                >
                    {audienceGroups.map((group, index) => {
                        const itemValue = index + 1;
                        return (
                            <AccordionItem key={group.title} value={itemValue}>
                                <AccordionHeader
                                    size="extra-large"
                                    className={styles.accordionHeader}
                                    expandIcon={openItem === itemValue ? <Subtract20Filled/> : <Add20Filled/>}
                                >
                                    {group.title}
                                </AccordionHeader>
                                <AccordionPanel className={styles.accordionPanel}>
                                    <ul className={styles.list}>
                                        {group.items.map((item) => (
                                            <li key={item.title}>
                                                <Text weight="semibold">{item.title}</Text>
                                                <Text size={300}>{item.body}</Text>
                                            </li>
                                        ))}
                                    </ul>
                                </AccordionPanel>
                            </AccordionItem>
                        );
                    })}
                </Accordion>
            </section>
        </section>
    );
}

