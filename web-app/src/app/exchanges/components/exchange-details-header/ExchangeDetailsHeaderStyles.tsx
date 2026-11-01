import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsHeaderStyles = makeStyles({

    container: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        background: tokens.colorNeutralBackground1,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalL}`,
        borderRadius: tokens.borderRadiusMedium,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRight: `1px solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        boxShadow: tokens.shadow4,
        "@media (max-width: 768px)": {
            padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalS}`,
            gap: tokens.spacingHorizontalXS,
        },
    },

    header: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
        minWidth: 0,
    },

    headerLine1: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    headerAnimatedSection: {
        overflow: "hidden",
        transition: "max-height 200ms ease, opacity 180ms ease, margin-top 180ms ease",
        maxHeight: "0px",
        opacity: 1,
        marginTop: "0",
    },

    headerAnimatedSectionExpanded: {
        maxHeight: "64px",
        opacity: 1,
        marginTop: tokens.spacingVerticalXS,
    },
    headerAnimatedSectionExpandedLarge: {
        maxHeight: "96px",
        opacity: 1,
        marginTop: tokens.spacingVerticalXS,
    },

    headerLine1_2: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        gap: tokens.spacingHorizontalS
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    headerLine3: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between"
    },

    containerStatusINITIATED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteGreenForeground1
    },

    containerStatusACCEPTED_STARTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteBlueForeground2
    },

    containerStatusENDED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorNeutralForeground4
    },

    containerStatusREJECTED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteRedForeground1
    },

    containerStatusRESCINDED: {
        borderLeft: "5px solid",
        borderLeftColor: tokens.colorPaletteDarkOrangeForeground1
    },
});
