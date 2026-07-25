import { makeStyles, tokens } from "@fluentui/react-components";

export const useExchangeWorkflowTabStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        height: "100%",
        minHeight: 0,
    },
    header: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        backgroundColor: tokens.colorNeutralBackground1,
        flexShrink: 0,
    },
    headerTitle: {
        fontSize: tokens.fontSizeBase400,
    },
    content: {
        flexGrow: 1,
        minHeight: 0,
        overflowY: "auto",
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        padding: `0 ${tokens.spacingHorizontalXXS}`,
        boxSizing: "border-box"
    },
    instanceSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        // padding: `0 ${tokens.spacingHorizontalL}`,
    },
    // Side-by-side "Both" view: detail/actions on the left, diagram preview
    // on the right at 60% width. Stacks to a single column on narrow
    // viewports so the diagram never gets squeezed unreadably thin.
    instanceSectionSplit: {
        display: "flex",
        flexDirection: "row",
        alignItems: "flex-start",
        gap: tokens.spacingHorizontalL,
        // padding: `0 ${tokens.spacingHorizontalL}`,
        "@media (max-width: 900px)": {
            flexDirection: "column",
        },
    },
    splitDetailColumn: {
        flex: "0 0 60%",
        minWidth: 0,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        "@media (max-width: 900px)": {
            flex: "1 1 auto",
            width: "100%",
        },
    },
    splitDiagramColumn: {
        flex: "1",
        minWidth: 0,
        "@media (max-width: 900px)": {
            flex: "1 1 auto",
            width: "100%",
        },
    },
    sectionHeading: {
        paddingBottom: tokens.spacingVerticalXS,
        borderBottom: `1px solid ${tokens.colorNeutralStroke2}`,
        marginBottom: tokens.spacingVerticalXS,
    },
    // WorkflowSummaryCard
    summaryCard: {
        padding: tokens.spacingHorizontalM,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
    },
    summaryNameRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
    summaryMetaRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalXS,
    },
    progressRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalXS,
    },
    metaText: {
        border: "5px solid red"
    },
    progressBar: {
        flexGrow: 1,
    },
    slaRow: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalXS,
    },
    // ActionRequiredCard
    actionCard: {
        padding: tokens.spacingHorizontalM,
        borderLeft: `3px solid ${tokens.colorBrandBackground}`,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
    },
    actionButtons: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        marginTop: tokens.spacingVerticalS,
        flexWrap: "wrap",
    },
    rejectForm: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
        marginTop: tokens.spacingVerticalXS,
    },
    rejectActions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
    },
    // RejectionBanner (MessageBar handles most styles; used for wrapper margin)
    rejectionBanner: {
        marginBottom: tokens.spacingVerticalXS,
    },
    // WorkflowTimeline / WorkflowTimelineItem
    timeline: {
        display: "flex",
        flexDirection: "column",
    },
    stepHeaderContent: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        width: "100%",
        minWidth: 0,
        flexWrap: "wrap",
    },
    stepHeaderTitle: {
        flex: "unset",
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    stepHeaderMeta: {
        display: "flex",
        alignItems: "center",
        flex: "1 1 16rem",
        minWidth: 0,
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        rowGap: tokens.spacingVerticalXXS,
        color: tokens.colorNeutralForeground3,
        marginLeft: "auto",
        justifyContent: "flex-end",
        "@media (max-width: 1200px)": {
            marginLeft: 0,
            width: "100%",
            justifyContent: "flex-start",
        },
    },
    stepHeaderMetaText: {
        minWidth: 0,
        overflowWrap: "anywhere",
    },
    stepDetail: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        paddingLeft: tokens.spacingHorizontalXXL,
        paddingBottom: tokens.spacingVerticalS,
    },
    decisionRow: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXXS,
        padding: tokens.spacingHorizontalS,
        borderLeft: `4px solid ${tokens.colorNeutralStroke2}`,
        marginBottom: tokens.spacingVerticalXS,
    },
    metaText: {
        color: tokens.colorNeutralForeground3,
    },
    stepIconSuccess: {
        color: tokens.colorStatusSuccessForeground1,
        flexShrink: 0,
    },
    stepIconDanger: {
        color: tokens.colorStatusDangerForeground1,
        flexShrink: 0,
    },
    stepIconWarning: {
        color: tokens.colorStatusWarningForeground1,
        flexShrink: 0,
    },
    stepIconNeutral: {
        color: tokens.colorNeutralForeground3,
        flexShrink: 0,
    },
    stepIconBrand: {
        color: tokens.colorBrandBackground,
        flexShrink: 0,
    },
    decisionApproveText: {
        color: tokens.colorStatusSuccessForeground1,
    },
    decisionRejectText: {
        color: tokens.colorStatusDangerForeground1,
    },
    emptyCard: {
        padding: tokens.spacingHorizontalM,
        color: tokens.colorNeutralForeground3,
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
    },
    centeredState: {
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        paddingTop: tokens.spacingVerticalXXL,
        gap: tokens.spacingVerticalM,
    },
    clearanceCard: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalXS,
        padding: `${tokens.spacingVerticalS} ${tokens.spacingHorizontalM}`,
        backgroundColor: tokens.colorNeutralBackground2,
        borderRadius: tokens.borderRadiusMedium,
    },
    clearanceBadgeRow: {
        display: "flex",
        flexWrap: "wrap",
        gap: tokens.spacingHorizontalS,
        alignItems: "center",
    },
});
