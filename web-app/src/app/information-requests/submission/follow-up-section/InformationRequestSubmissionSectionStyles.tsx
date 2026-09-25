import {makeStyles, tokens} from "@fluentui/react-components";

export const useInformationRequestSubmissionSectionStyles = makeStyles({
    section: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingVerticalL,
        minWidth: 0,
    },
    actions: {
        display: "flex",
        justifyContent: "flex-end",
        gap: tokens.spacingHorizontalS,
        flexWrap: "wrap",
    },
});
