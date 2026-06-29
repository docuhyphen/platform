import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileOverviewCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: "20px",
        ...shorthands.padding("24px"),
        ...shorthands.borderRadius(tokens.borderRadiusXLarge),
    },

    content: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: "16px",
        flexWrap: "wrap",
    },

    identityBlock: {
        display: "flex",
        alignItems: "center",
        gap: "16px",
        flexWrap: "wrap",
    },

    copyBlock: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        minWidth: 0,
    },

    badgeRow: {
        display: "flex",
        gap: "8px",
        flexWrap: "wrap",
    },

    metaGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: "12px",
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    metaItem: {
        display: "flex",
        flexDirection: "column",
        gap: "6px",
        ...shorthands.padding("14px", "16px"),
        ...shorthands.borderRadius(tokens.borderRadiusLarge),
        backgroundColor: tokens.colorNeutralBackground3,
    },

    eyebrow: {
        color: tokens.colorBrandForeground1,
    },

    subtleText: {
        color: tokens.colorNeutralForeground3,
    },

    inlineDetailRow: {
        display: "flex",
        alignItems: "center",
        gap: "6px",
        minWidth: 0,
    }
});
