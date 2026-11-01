import {makeStyles, tokens} from "@fluentui/react-components";

export const useWorkflowStepIconStyles = makeStyles({
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
});
