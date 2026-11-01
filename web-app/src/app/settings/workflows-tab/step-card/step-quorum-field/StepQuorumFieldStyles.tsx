import {makeStyles, tokens} from "@fluentui/react-components";
import {stepCardFieldStyle} from "../stepCardStyleFragments.ts";

export const useStepQuorumFieldStyles = makeStyles({
    field: stepCardFieldStyle,

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
});
