import {makeStyles, tokens} from "@fluentui/react-components";

export const useLegalDocumentPageStyles = makeStyles({
    wrapper: {
        display: "flex",
        flexDirection: "column",
        width: "100%",
        maxWidth: "48rem",
        margin: "0 auto",
        paddingTop: tokens.spacingVerticalXXXL,
        paddingRight: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalXXXL,
        paddingLeft: tokens.spacingHorizontalL,
        boxSizing: "border-box",
    },
    header: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        textAlign: "center",
        gap: tokens.spacingVerticalS,
        paddingBottom: tokens.spacingVerticalXXL,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
    },
    label: {
        color: tokens.colorBrandForeground1,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        letterSpacing: "0.08em",
        textTransform: "uppercase",
    },
    title: {
        color: tokens.colorNeutralForeground1,
    },
    effectiveDate: {
        color: tokens.colorNeutralForeground3,
    },
    summary: {
        color: tokens.colorNeutralForeground2,
        maxWidth: "40rem",
        lineHeight: tokens.lineHeightBase500,
    },
    content: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXXL,
        paddingTop: tokens.spacingVerticalXXL,
    },
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    sectionTitle: {
        color: tokens.colorNeutralForeground1,
        margin: 0,
    },
    paragraph: {
        color: tokens.colorNeutralForeground2,
        lineHeight: tokens.lineHeightBase400,
        margin: 0,
    },
    actions: {
        display: "flex",
        flexWrap: "wrap",
        justifyContent: "center",
        gap: tokens.spacingHorizontalM,
        paddingTop: tokens.spacingVerticalXXXL,
    },
});
