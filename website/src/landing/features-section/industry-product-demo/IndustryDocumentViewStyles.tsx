import {makeStyles, tokens} from "@fluentui/react-components";
import {BREAKPOINT_MOBILE} from "../../shared.ts";

export const useIndustryDocumentViewStyles = makeStyles({
    detail: {
        display: "flex",
        flexDirection: "column",
        minWidth: 0,
        flex: 1,
        backgroundColor: tokens.colorNeutralBackground2,
    },

    searchRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    searchActions: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
        flexShrink: 0,

        [BREAKPOINT_MOBILE]: {
            flexShrink: 1,
        },
    },

    search: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        width: "42%",
        minWidth: "11rem",
        minHeight: "2rem",
        paddingRight: tokens.spacingHorizontalS,
        paddingLeft: tokens.spacingHorizontalS,
        border: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke1}`,
        borderRadius: tokens.borderRadiusMedium,
        color: tokens.colorNeutralForeground3,
        fontSize: tokens.fontSizeBase100,

        [BREAKPOINT_MOBILE]: {
            width: "100%",
            minWidth: 0,
        },
    },

    progressSummary: {
        display: "flex",
        alignItems: "center",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexShrink: 0,

        [BREAKPOINT_MOBILE]: {
            display: "none",
        },
    },

    progressText: {
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
    },

    progressBar: {
        width: "88px",
    },

});
