import {makeStyles, tokens} from "@fluentui/react-components";

export const useEscalationTargetsFieldStyles = makeStyles({
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
