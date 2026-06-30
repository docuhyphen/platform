import {makeStyles, tokens} from "@fluentui/react-components";

export const useExchangeDetailsHeaderStyles = makeStyles({

    container: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        background: tokens.colorNeutralBackground1,
        padding: "8px 16px",
        borderRadius: tokens.borderRadiusMedium,
        borderTop: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRight: `1px solid ${tokens.colorNeutralStroke2}`,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        boxShadow: tokens.shadow4,
        // Tighter padding on phones - the exchange header is the most
        // important navigation context on mobile and must give as much
        // room as possible to the title + back button.
        "@media (max-width: 768px)": {
            padding: "6px 8px",
            gap: "4px",
        },
    },

    header: {
        display: "flex",
        flexDirection: "column",
        flex: "1",
        // Allow the header column to shrink below its intrinsic content
        // width so the title row's truncation can take effect on narrow
        // viewports. Without this, long exchange names push the actions
        // cluster off-screen instead of getting an ellipsis.
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
        opacity: 0,
        marginTop: "0px",
    },

    headerAnimatedSectionExpanded: {
        maxHeight: "80px",
        opacity: 1,
        marginTop: "4px",
    },

    headerLine1_2: {
        display: "flex",
        flexDirection: "row",
        alignItems: "center",
        gap: "8px"
    },

    headerLine2: {
        display: "flex",
        flexDirection: "row",
        justifyContent: "space-between",
        alignItems: "center",
        gap: "8px",
        minWidth: 0,
    },

    /** Title + (mobile-only) back-to-list button, kept on one row. */
    headerTitleGroup: {
        display: "flex",
        alignItems: "center",
        gap: "4px",
        flex: 1,
        minWidth: 0,
    },

    headerTitleText: {
        // Flex row that owns the truncation: the inner <Text>
        // (an inline <span>) is stretched/clipped by this wrapper while
        // align-items keeps it vertically centered with the adjacent
        // back-to-list button.
        display: "flex",
        alignItems: "center",
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
        // Ensure the immediate Text child shrinks and truncates.
        "& > *": {
            minWidth: 0,
            maxWidth: "100%",
            overflow: "hidden",
            textOverflow: "ellipsis",
            whiteSpace: "nowrap",
        },
        // On compact viewports the default FluentUI Text sizes 500/600
        // (20px / 24px) are too tall and force the toolbar onto the
        // next line. Override the font + line-height directly on the
        // child Text so the title stays comfortable on phones.
        "@media (max-width: 768px)": {
            "& > *": {
                fontSize: tokens.fontSizeBase400,
                lineHeight: tokens.lineHeightBase400,
            },
        },
        "@media (max-width: 480px)": {
            "& > *": {
                fontSize: tokens.fontSizeBase300,
                lineHeight: tokens.lineHeightBase300,
            },
        },
    },

    backToListButton: {
        flexShrink: 0,
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

    actions: {
        display: "flex",
        gap: "8px",
        alignSelf: "center",
        flexShrink: 0,
        "@media (max-width: 768px)": {
            gap: "2px",
        },
    },
});
