import {makeStyles, tokens} from "@fluentui/react-components";
import {stepCardOutcomeRowStyle} from "../stepCardStyleFragments.ts";

export const useStepOutcomesSectionStyles = makeStyles({
    outcomeRow: stepCardOutcomeRowStyle,

    waitDescription: {
        color: tokens.colorNeutralForeground3,
    },
});
