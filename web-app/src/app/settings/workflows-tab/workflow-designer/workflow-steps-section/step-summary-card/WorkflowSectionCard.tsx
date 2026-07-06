import {Card, Text} from "@fluentui/react-components";
import {ReactNode} from "react";
import {useWorkflowSectionCardStyles} from "./WorkflowSectionCardStyles.tsx";

interface Props
{
    id: string;
    title: string;
    description: string;
    summary: string;
    icon: ReactNode;
    onClick: () => void;
}

const WorkflowSectionCard = ({id, title, description, summary, icon, onClick}: Props) =>
{
    const styles = useWorkflowSectionCardStyles();

    return (
        <Card
            id={id}
            className={styles.card}
            appearance="outline"
            role="button"
            tabIndex={0}
            aria-label={`Open ${title}`}
            onClick={onClick}
            onKeyDown={event =>
            {
                if (event.key === "Enter" || event.key === " ")
                {
                    event.preventDefault();
                    onClick();
                }
            }}
        >
            <div
                id={`${id}-heading`}
                className={styles.heading}
            >
                <span
                    id={`${id}-icon`}
                    className={styles.icon}
                    aria-hidden="true"
                >
                    {icon}
                </span>
                <Text
                    id={`${id}-title`}
                    weight="semibold"
                    size={400}
                >
                    {title}
                </Text>
            </div>
            <Text
                id={`${id}-description`}
                size={200}
                className={styles.description}
            >
                {description}
            </Text>
            <Text
                id={`${id}-summary`}
                size={200}
                weight="semibold"
                className={styles.summary}
            >
                {summary}
            </Text>
        </Card>
    );
};

export default WorkflowSectionCard;
