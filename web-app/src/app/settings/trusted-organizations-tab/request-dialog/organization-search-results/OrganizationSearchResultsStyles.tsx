import {makeStyles, shorthands, tokens} from "@fluentui/react-components";

export const useOrganizationSearchResultsStyles = makeStyles({
    root: {
        display: "grid",
        gap: tokens.spacingVerticalXS,
        maxHeight: "14rem",
        overflowY: "auto",
    },
    result: {
        justifyContent: "flex-start",
        ...shorthands.padding(tokens.spacingVerticalS, tokens.spacingHorizontalM),
    },
    selected: {
        backgroundColor: tokens.colorNeutralBackground1Selected,
    },
});
