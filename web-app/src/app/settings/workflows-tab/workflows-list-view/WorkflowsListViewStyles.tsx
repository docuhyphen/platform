import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowsListViewStyles = makeStyles({
    container: {
        height: "100%",
        minHeight: 0,
        display: "flex",
        flexDirection: "column",
    },
    section: {
        marginBottom: tokens.spacingVerticalXXXL,
    },

    sectionHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        marginBottom: tokens.spacingVerticalM,
    },

    emptyState: {
        padding: tokens.spacingHorizontalXXL,
        color: tokens.colorNeutralForeground3,
        textAlign: "center",
        border: `1px dashed ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    row: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalSNudge,
        padding: `${tokens.spacingVerticalM} ${tokens.spacingHorizontalL}`,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        minWidth: 0,
        overflow: "hidden",
    },

    topRow: {
        display: "flex",
        justifyContent: "space-between",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    triggerText: {
        flexShrink: 0,
        color: tokens.colorNeutralForeground3,
    },

    middleRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    description: {
        flex: 1,
        overflow: "hidden",
        textOverflow: "ellipsis",
        whiteSpace: "nowrap",
        minWidth: 0,
        color: tokens.colorNeutralForeground2,
    },

    middleActions: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
        flexShrink: 0,
    },

    tagsContainer: {
        display: "flex",
        overflow: "hidden",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    templateCard: {
        padding: tokens.spacingHorizontalL,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
        display: "flex",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
    },

    templateCardInfo: {
        flex: 1,
        minWidth: 0,
    },

    tagRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalXS,
        marginTop: tokens.spacingVerticalXS,
    },

    errorBar: {
        marginBottom: tokens.spacingVerticalM,
    },

    cardGrid: {
        display: "grid",
        gridTemplateColumns: "repeat(2, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media screen and (max-width: 768px)": {
            gridTemplateColumns: "1fr",
        },
    },

    outerWrapper: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        background: tokens.colorNeutralBackground1,
        paddingBottom: tokens.spacingVerticalM,
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        flexShrink: 0,
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },
    scrollableContent: {
        flex: 1,
        minHeight: 0,
        overflowY: "auto",
        overflowX: "hidden",
        overscrollBehavior: "contain",
        paddingInline: tokens.spacingHorizontalS,
        boxSizing: "border-box",
    },

    cloneNameLabel: {
        display: "block",
        marginBottom: tokens.spacingVerticalXS,
    },

    cloneNameInput: {
        width: "100%",
    },

    platformCardTriggerText: {
        marginTop: tokens.spacingVerticalXXS,
    },

    overflowTagsButton: {
        padding: `0 ${tokens.spacingHorizontalSNudge}`,
        minWidth: "0",
    },
    table: {
        width: "100%",
        borderCollapse: "collapse",
    },
    th: {
        textAlign: "left",
        padding: `${tokens.spacingVerticalSNudge} ${tokens.spacingHorizontalM}`,
        fontSize: tokens.fontSizeBase200,
        fontWeight: tokens.fontWeightSemibold,
        color: tokens.colorNeutralForeground3,
        borderBottom: `1px solid ${tokens.colorNeutralStroke1}`,
        whiteSpace: "nowrap",
    },
    tr: {
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        ":hover": {backgroundColor: tokens.colorNeutralBackground2},
    },
    td: {
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        verticalAlign: "middle",
    },
});
