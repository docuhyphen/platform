import {makeStyles} from "@fluentui/react-components";

export const useOperationsStateFilterStyles = makeStyles({
    field: {
        minWidth: "220px",
        maxWidth: "100%",
        "@media (max-width: 640px)": {
            width: "100%",
        },
    },
});
