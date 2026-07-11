import {makeStyles, tokens} from "@fluentui/react-components";

export const useCommunicationsToolbarStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        background: tokens.colorNeutralBackground1,
        paddingInline: tokens.spacingHorizontalS,
        paddingBottom: tokens.spacingVerticalS,
        boxSizing: "border-box",
        flexShrink: 0,
        "@media screen and (max-width: 600px)": {
            alignItems: "stretch",
            flexDirection: "column",
        },
    },
    leftControls: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingVerticalS,
        flex: "0 1 auto",
        minWidth: 0,
        "@media screen and (max-width: 600px)": {
            width: "100%",
        },
    },
    searchField: {
        flex: "0 1 420px",
        minWidth: 0,
        width: "420px",
        maxWidth: "100%",
        "@media screen and (max-width: 600px)": {
            flex: 1,
            width: "auto",
        },
    },
    filterPopover: {
        padding: tokens.spacingHorizontalS,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        minWidth: "220px",
        maxWidth: "280px",
    },
    filterSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
    },
    filterSectionTitle: {
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
    },
    filterList: {
        minHeight: "32px",
        maxHeight: "180px",
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
    },
    emptyText: {
        color: tokens.colorNeutralForeground3,
        padding: `${tokens.spacingVerticalXS} 0`,
    },
});
