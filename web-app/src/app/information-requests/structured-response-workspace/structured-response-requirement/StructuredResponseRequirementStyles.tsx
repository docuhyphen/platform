import {makeStyles, tokens} from "@fluentui/react-components";

export const useStructuredResponseRequirementStyles = makeStyles({
    requirement: {
        display: "flex",
        flexDirection: "column",
        gap: tokens.spacingHorizontalM,
        minWidth: 0,
    },
});
