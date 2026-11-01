import {makeStyles} from "@fluentui/react-components";
import {stepCardFieldStyle} from "../stepCardStyleFragments.ts";

export const useStepIdentityFieldsStyles = makeStyles({
    field: stepCardFieldStyle,

    fullWidth: {
        gridColumn: "1 / -1",
    },
});
