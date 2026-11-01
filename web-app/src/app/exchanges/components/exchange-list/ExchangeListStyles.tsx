import {makeStyles, tokens} from "@fluentui/react-components";

const useExchangeStyles = makeStyles({

    exchangesListContainer: {
        width: "400px",
        minWidth: "400px",
        maxWidth: "400px",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        position: "relative",
        borderRadius: tokens.borderRadiusMedium,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        boxSizing: "border-box",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
        "@media (max-width: 768px)": {
            width: "100%",
            minWidth: 0,
            maxWidth: "100%",
            flex: 1,
        },
    },

    exchangesListContainerCollapsed: {
        width: "2.8rem",
        minWidth: "2.8rem",
        maxWidth: "2.8rem",
        overflow: "hidden",
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        position: "relative",
        borderRadius: tokens.borderRadiusMedium,
        background: tokens.colorNeutralBackground1,
        display: "flex",
        flexDirection: "column",
        boxSizing: "border-box",
        transition: "width 220ms ease, min-width 220ms ease, max-width 220ms ease",
        "@media (max-width: 768px)": {
            width: "100%",
            minWidth: 0,
            maxWidth: "100%",
            flex: 1,
        },
    },

    listCard: {
        width: "100%",
        padding: tokens.spacingHorizontalS,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        boxSizing: "border-box",
        minWidth: 0,
        overflow: "hidden",
    },

    listCardItem: {
        display: "flex",
        flexDirection: "row",
        gap: tokens.spacingHorizontalM,
        boxSizing: "border-box",
        maxWidth: "100%",
        minWidth: 0,
    },

    listCardItemDetails: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        flex: 1,
        minWidth: 0,
    },

    listCardItemRow: {
        display: "flex",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    exchangesListSelectedItem: {
        background: tokens.colorNeutralBackground2,
        borderLeft: "3px solid",
        transition: "all 0.1s ease",
        borderLeftColor: tokens.colorBrandForeground1,
    },
});

export {useExchangeStyles};
export default useExchangeStyles;
