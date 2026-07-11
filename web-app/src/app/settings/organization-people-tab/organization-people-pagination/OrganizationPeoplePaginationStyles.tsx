import {tokens, makeStyles} from "@fluentui/react-components";

export const useOrganizationPeoplePaginationStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: tokens.spacingHorizontalM,
        width: "100%",
        "@media (max-width: 600px)": {
            alignItems: "flex-start",
            flexDirection: "column"
        }
    },
    controls: {
        display: "flex",
        alignItems: "center",
        gap: tokens.spacingHorizontalS
    }
});
