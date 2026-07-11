import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useProfileOverviewCardStyles = makeStyles({
    card: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXL,
        ...shorthands.padding(tokens.spacingHorizontalXXL),
        ...shorthands.borderRadius(tokens.borderRadiusXLarge),
    },

    content: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },

    identityBlock: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalL,
        flexWrap: "wrap",
    },

    copyBlock: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    badgeRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },

    metaGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
        gap: tokens.spacingHorizontalM,
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    metaItem: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalL),
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
        gap: tokens.spacingHorizontalSNudge,
        minWidth: 0,
    }
});
