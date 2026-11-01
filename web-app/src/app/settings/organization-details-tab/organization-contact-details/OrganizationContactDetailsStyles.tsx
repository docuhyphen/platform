import {makeStyles, tokens} from "@fluentui/react-components";

export const useOrganizationContactDetailsStyles = makeStyles({
    dataEditable: {
        display: "flex",
        gap: tokens.spacingHorizontalS
    },
    mainDivider: {
        width: "100%",
        maxWidth: "300px"
    }
});
