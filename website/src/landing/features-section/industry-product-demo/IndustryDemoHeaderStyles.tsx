import {makeStyles, tokens} from "@fluentui/react-components";

export const useIndustryDemoHeaderStyles = makeStyles({
    header: {
        display: "flex",
        width: "100%",
        alignItems: "center",
        justifyContent: "space-between",
        boxSizing: "border-box",
        height: "3.75rem",
        gap: tokens.spacingHorizontalS,
        paddingRight: tokens.spacingHorizontalL,
        paddingLeft: tokens.spacingHorizontalL,
        borderBottom: `${tokens.strokeWidthThin} solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
    },

    brand: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flex: 1,
        minWidth: 0,
        overflow: "hidden",
    },

    logo: {
        display: "block",
        width: "3.1rem",
        height: "auto",
    },

    organizationShortName: {
        color: tokens.colorNeutralForeground1,
        fontSize: tokens.fontSizeBase100,
        opacity: 0.3,
    },

    actions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    startButton: {
        whiteSpace: "nowrap",
    },

    iconButton: {
        color: tokens.colorNeutralForeground2,
    },

    notificationControl: {
        position: "relative",
        display: "flex",
    },

    notificationIndicator: {
        position: "absolute",
        top: "0.375rem",
        right: "0.375rem",
        width: "0.5rem",
        height: "0.5rem",
        borderRadius: tokens.borderRadiusCircular,
        backgroundColor: tokens.colorPaletteRedBackground3,
        boxShadow: `0 0 0 ${tokens.strokeWidthThick} ${tokens.colorNeutralBackground1}`,
        pointerEvents: "none",
    },

    persona: {
        minWidth: 0,
        paddingRight: tokens.spacingHorizontalXS,
        paddingLeft: tokens.spacingHorizontalXS,
    },
});
