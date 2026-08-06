import {Badge} from "@fluentui/react-components";
import {useInternalNoteVisibilityBadgeStyles} from "./InternalNoteVisibilityBadgeStyles.tsx";

interface InternalNoteVisibilityBadgeProps
{
    id: string;
    organizationName?: string;
}

const InternalNoteVisibilityBadge = (
    {
        id,
        organizationName,
    }: InternalNoteVisibilityBadgeProps) =>
{
    const styles = useInternalNoteVisibilityBadgeStyles();

    return (
        <Badge
            id={id}
            className={styles.badge}
            appearance={"tint"}
            color={"brand"}
            size={"small"}
        >
            Visible only to active members of {organizationName}.
        </Badge>
    );
};

export default InternalNoteVisibilityBadge;
