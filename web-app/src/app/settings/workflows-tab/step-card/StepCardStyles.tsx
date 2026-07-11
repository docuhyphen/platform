import {makeStyles, tokens} from "@fluentui/react-components";

export const useStepCardStyles = makeStyles({
    card: {
        minWidth: 0,
    },

    cardHeader: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalS,
        padding: `${tokens.spacingVerticalMNudge} ${tokens.spacingHorizontalL}`,
        background: tokens.colorNeutralBackground2,
        position: "sticky",
        top: 0,
        zIndex: 2,
        border: `1px solid ${tokens.colorNeutralStroke2}`,
        borderRadius: tokens.borderRadiusMedium,
    },

    cardBody: {
        paddingTop: tokens.spacingVerticalM,
        paddingRight: tokens.spacingHorizontalL,
        paddingBottom: tokens.spacingVerticalL,
        paddingLeft: tokens.spacingHorizontalL,
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
    },

    fieldGroup: {
        display: "grid",
        gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
        gap: tokens.spacingHorizontalM,
        "@media (max-width: 900px)": {
            gridTemplateColumns: "1fr",
        },
    },

    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    fullWidth: {
        gridColumn: "1 / -1",
    },

    outcomeRow: {
        display: "flex",
        gap: tokens.spacingVerticalS,
        flexDirection: "column"
    },

    sectionLabel: {
        color: tokens.colorNeutralForeground3,
        marginBottom: tokens.spacingVerticalXS,
    },

    removeButton: {
        marginLeft: "auto",
        color: tokens.colorStatusDangerForeground1,
    },

    outcomeFieldColumn: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    outcomeFieldRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        minWidth: 0,
    },

    outcomeEmitSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },

    quorumRow: {
        display: "flex",
        gap: tokens.spacingHorizontalXS,
        flexWrap: "wrap",
        minWidth: 0,
    },

    quorumNInput: {
        width: "4rem",
        flexShrink: 0,
    },

    notificationRow: {
        display: "flex",
        gap: tokens.spacingHorizontalSNudge,
        alignItems: "center",
        flexWrap: "wrap",
    },

    notificationHint: {
        color: tokens.colorNeutralForeground3,
    },

    conditionHint: {
        color: tokens.colorNeutralForeground3,
    },

    conditionRow: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
        alignItems: "center",
    },

    conditionFieldCombobox: {
        flex: "1 1 10rem",
        minWidth: "0",
    },

    conditionOperatorSelect: {
        flex: "1 1 10rem",
        minWidth: 0,
    },

    conditionValueSelect: {
        flex: "1 1 8rem",
        minWidth: 0,
    },

    conditionValueInput: {
        flex: "1 1 8rem",
        minWidth: 0,
    },

    entityPickerCombobox: {
        flex: "1 1 8rem",
        minWidth: "0",
    },

    sublabel: {
        color: tokens.colorNeutralForeground3,
        marginLeft: tokens.spacingHorizontalS,
        fontSize: "0.8em",
    },

    waitDescription: {
        color: tokens.colorNeutralForeground3,
    },

    conditionPreview: {
        color: tokens.colorNeutralForeground3,
    },
});

