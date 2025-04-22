import {makeStyles} from "@fluentui/react-components";

export const useOrganizationOnboardingForm = makeStyles({
    container: {
        display: "flex",
        flexDirection: "column",
        gap: "8px",
        flex: 1,
    },
    dialogActions: {
        display: "flex",
        justifyContent: "end",
        marginTop: "16px",
        gap: "8px"
    }
});