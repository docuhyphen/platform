import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useExchangeDocumentsListStyles = makeStyles({
    container: {
        width: "100%",
        overflow: "hidden",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    toolbar: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        width: "100%",
    },
    toolbarWrapper: {
        width: "100%",
        overflow: "hidden",
        transformOrigin: "top",
    },
    toolbarEntering: {
        animationName: {
            from: {opacity: 0, transform: "translateY(-8px) scaleY(0.95)"},
            to: {opacity: 1, transform: "translateY(0) scaleY(1)"},
        },
        animationDuration: "180ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    toolbarLeaving: {
        animationName: {
            from: {opacity: 1, transform: "translateY(0) scaleY(1)"},
            to: {opacity: 0, transform: "translateY(-8px) scaleY(0.95)"},
        },
        animationDuration: "180ms",
        animationTimingFunction: "cubic-bezier(0.2, 0, 0, 1)",
    },
    searchField: {
        flex: "1 1 280px",
        width: "100%",
        maxWidth: "360px",
        minWidth: "220px",
    },
    filterGroup: {
        display: "flex",
        alignItems: "center",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
    },
    toolbarActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        marginLeft: "auto",
        "@media (max-width: 900px)": {
            marginLeft: 0,
        },
    },
    stripLayout: {
        display: "grid",
        gridTemplateColumns: "auto minmax(0, 1fr) auto",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        width: "100%",
    },
    cardListSection: {
        display: "flex",
        overflowX: "auto",
        overflowY: "hidden",
        scrollBehavior: "smooth",
        scrollSnapType: "x proximity",
        WebkitOverflowScrolling: "touch",
        ...shorthands.padding(tokens.spacingVerticalXS, tokens.spacingHorizontalXXS),
        gap: tokens.spacingHorizontalM,
        scrollbarWidth: "thin",
        scrollbarColor: `${tokens.colorNeutralStroke1} transparent`,
        "&::-webkit-scrollbar": {
            height: "6px",
        },
        "&::-webkit-scrollbar-thumb": {
            backgroundColor: tokens.colorNeutralStroke1,
            borderRadius: tokens.borderRadiusCircular,
        },
        "&::-webkit-scrollbar-track": {
            backgroundColor: "transparent",
        },
    },
    cardListSectionFullWidth: {
        gridColumn: "1 / -1",
    },
    scrollButton: {
        flexShrink: 0,
    },
    documentsCard: {
        width: "300px",
        minWidth: "300px",
        maxWidth: "300px",
        flex: "0 0 300px",
        scrollSnapAlign: "start",
        cursor: "pointer",
        gap: tokens.spacingVerticalXS,
        boxSizing: "border-box",
        ...shorthands.padding(tokens.spacingVerticalM, tokens.spacingHorizontalM),
        ...shorthands.borderLeft("5px", "solid", "transparent"),
        ":hover": {
            backgroundColor: tokens.colorNeutralBackground1Hover,
        },
        ":focus-visible": {
            ...shorthands.outline("2px", "solid", tokens.colorStrokeFocus2),
            outlineOffset: "-2px",
        },
    },
    documentsCardSelected: {
        borderLeftColor: tokens.colorBrandForeground1,
        backgroundColor: tokens.colorNeutralBackground1Selected,
        boxShadow: tokens.shadow4,
    },
    cardTitle: {
        display: "block",
        width: "100%",
        minWidth: 0,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        lineHeight: tokens.lineHeightBase300,
    },
    cardFooter: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },
    statusGroup: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
        color: tokens.colorNeutralForeground2,
    },
    uploadedIcon: {
        color: tokens.colorPaletteGreenForeground1,
        flexShrink: 0,
    },
    uploadDate: {
        color: tokens.colorNeutralForeground3,
        whiteSpace: "nowrap",
    },
    cardActions: {
        display: "flex",
        alignItems: "center",
        flexShrink: 0,
        gap: tokens.spacingHorizontalXXS,
    },
});
