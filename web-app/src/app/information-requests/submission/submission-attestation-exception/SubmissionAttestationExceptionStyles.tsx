import {makeStyles, tokens} from "@fluentui/react-components";

export const useSubmissionAttestationExceptionStyles = makeStyles({
    exception: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalS,
    },
    detail: {
        color: tokens.colorNeutralForeground3,
    },
    actions: {
        display: "flex",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
