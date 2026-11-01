import {makeStyles, tokens} from "@fluentui/react-components";

export const useHelpDocumentationHeaderStyles = makeStyles({
    header: {
        position: "sticky",
        top: 0,
        zIndex: 1,
        backgroundColor: tokens.colorNeutralBackground1,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
    },
    breadcrumbRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalSNudge,
        color: tokens.colorNeutralForeground3,
    },
    crumbSeparator: {
        color: tokens.colorNeutralForeground4,
    },
    titleRow: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
    },
    titleText: {
        fontWeight: tokens.fontWeightSemibold,
        flex: 1,
        minWidth: 0,
    },
    headerActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXXS,
        flexShrink: 0,
    },
});
