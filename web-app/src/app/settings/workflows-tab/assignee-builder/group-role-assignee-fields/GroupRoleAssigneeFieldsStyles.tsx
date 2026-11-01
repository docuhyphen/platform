import {makeStyles} from "@fluentui/react-components";
import {assigneeFlexFieldStyle} from "../assigneeBuilderStyleFragments.ts";

export const useGroupRoleAssigneeFieldsStyles = makeStyles({
    fieldInput: assigneeFlexFieldStyle,
    kindSelect: assigneeFlexFieldStyle,
});
