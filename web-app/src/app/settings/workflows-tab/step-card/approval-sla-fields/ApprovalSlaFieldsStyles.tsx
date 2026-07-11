import {makeStyles, tokens} from "@fluentui/react-components";

export const useApprovalSlaFieldsStyles = makeStyles({
    field: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalXS,
        minWidth: 0,
    },

    escalationTargets: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalS,
        minWidth: 0,
    },

    escalationHint: {
        color: tokens.colorNeutralForeground3,
    },
});
