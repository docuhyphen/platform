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
    SECTION_PADDING_DESKTOP,
    SECTION_PADDING_MOBILE,
    SPACE_LG,
    SPACE_SM,
    SPACE_XL,
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
        color: tokens.colorNeutralForegroundOnBrand,
        background: LAW_SECTION_GRADIENT,
        padding: SECTION_PADDING_DESKTOP,
        boxSizing: "border-box",
        borderTopLeftRadius: "36px",
        borderTopRightRadius: "36px",

        [BREAKPOINT_MOBILE]: {
            padding: SECTION_PADDING_MOBILE,
        },
    },

    intro: {
        display: "flex",
        flexDirection: "column",
        gap: SPACE_SM,
        maxWidth: WIDTH_CONTENT,
        margin: "0 auto",
        marginBottom: SPACE_XL,
        alignItems: "flex-start",
    },

    sectionTitle: {
        color: tokens.colorNeutralForegroundOnBrand,
        fontWeight: tokens.fontWeightSemibold,
        fontSize: tokens.fontSizeHero800
    },

    container: {
        boxSizing: "border-box",
        margin: "0 auto",
        maxWidth: WIDTH_CONTENT,
        width: "100%",
        color: tokens.colorNeutralForegroundOnBrand,
        backgroundColor: "transparent",
        display: "flex",
        flexDirection: "column",
        gap: SPACE_LG,
        padding: 0,

        [BREAKPOINT_MOBILE]: {
            padding: 0,
            margin: "0",
        },
    },

    subheading: {
        maxWidth: WIDTH_SUBTITLE,
        color: tokens.colorNeutralForegroundOnBrand,
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
        border: "3px dotted rgba(255, 255, 255, 0.3)",
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
                    body: "Secure Exchange of tax records, financial statements, and supporting documents.",
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
        {
            title: "Any document-driven team",
            items: [
                {
                    title: "Project and operations teams",
                    body: "Coordinate documents, responsibilities, and decisions across internal and external stakeholders.",
                },
                {
                    title: "Growing businesses",
                    body: "Replace scattered email attachments and shared-drive links with controlled document Exchanges.",
                },
                {
                    title: "Specialist service providers",
                    body: "Share client documents securely while keeping a clear record of every important action.",
                },
            ],
        },
    ];

    return (
        <section className={styles.wrapper}>
            <section className={styles.intro}>
                <Title2 className={styles.sectionTitle}>
                    More industry coverage
                </Title2>
                <Text className={styles.subheading} size={500}>
                    Explore additional use cases beyond the industry solutions above.
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

