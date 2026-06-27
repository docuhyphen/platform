import { makeStyles, tokens } from "@fluentui/react-components";

export const useExchangeWorkflowTabStyles = makeStyles({
    root: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        paddingTop: tokens.spacingVerticalM,
    },
    instanceSection: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalM,
        padding: "1rem"
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
        flexWrap: "wrap",
    },
    stepHeaderMeta: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS,
        color: tokens.colorNeutralForeground3,
        marginLeft: "auto",
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
        gap: "2px",
        padding: tokens.spacingHorizontalS,
        borderLeft: `4px solid ${tokens.colorNeutralStroke2}`,
        marginBottom: tokens.spacingVerticalXS,
    },
    metaText: {
        color: tokens.colorNeutralForeground3,
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
