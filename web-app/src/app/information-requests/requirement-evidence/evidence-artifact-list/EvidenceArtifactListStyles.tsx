import {makeStyles, tokens} from "@fluentui/react-components";

export const useEvidenceArtifactListStyles = makeStyles({
    artifacts: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    note: {
        color: tokens.colorNeutralForeground3,
    },
});
