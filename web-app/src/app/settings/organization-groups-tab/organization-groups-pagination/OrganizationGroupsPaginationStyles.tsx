import {makeStyles} from "@fluentui/react-components";

export const useOrganizationGroupsPaginationStyles = makeStyles({
    container: {
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: "12px",
        width: "100%",
        "@media (max-width: 600px)": {
            alignItems: "flex-start",
            flexDirection: "column"
        }
    },
    controls: {
        display: "flex",
        alignItems: "center",
        gap: "8px"
    }
});
