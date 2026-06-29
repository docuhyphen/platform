import {Card, Text} from "@fluentui/react-components";
import {ReactNode} from "react";
import {useProfileSectionCardStyles} from "./ProfileSectionCardStyles.tsx";

interface ProfileSectionCardProps
{
    id: string;
    title: string;
    description: string;
    children: ReactNode;
    action?: ReactNode;
}

const ProfileSectionCard = (
    {
        id,
        title,
        description,
        children,
        action
    }: ProfileSectionCardProps
) =>
{
    const styles = useProfileSectionCardStyles();

    return <Card id={id} className={styles.card}>
        <div id={`${id}-header`} className={styles.header}>
            <div id={`${id}-copy`} className={styles.copyBlock}>
                <Text id={`${id}-title`} size={500} weight={"semibold"}>{title}</Text>
                <Text id={`${id}-description`} size={200} className={styles.description}>{description}</Text>
            </div>
            {action && <div id={`${id}-action`} className={styles.action}>{action}</div>}
        </div>

        <div id={`${id}-content`} className={styles.content}>
            {children}
        </div>
    </Card>;
};

export default ProfileSectionCard;
