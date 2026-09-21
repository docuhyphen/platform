import {Badge, Button, Text} from "@fluentui/react-components";
import {
    InformationRequestTemplateStatus,
    InformationRequestTemplateSummaryDto,
} from "../../models/models.tsx";
import {useInformationRequestTemplatesTabStyles} from "./InformationRequestTemplatesTabStyles.tsx";

interface Props
{
    templates: InformationRequestTemplateSummaryDto[];
    loading: boolean;
    error: string | null;
    canManage: boolean;
    onOpenDraft: (template: InformationRequestTemplateSummaryDto) => void;
}

const InformationRequestTemplateList = ({templates, loading, error, canManage, onOpenDraft}: Props) =>
{
    const styles = useInformationRequestTemplatesTabStyles();

    if (loading)
    {
        return <Text id={"information-request-template-list-loading"}>Loading Templates...</Text>;
    }
    if (error)
    {
        return (
            <Text
                id={"information-request-template-list-error"}
                className={styles.errorText}
            >
                {error}
            </Text>
        );
    }
    if (templates.length === 0)
    {
        return (
            <Text
                id={"information-request-template-list-empty"}
                className={styles.secondaryText}
            >
                No Information Request Templates yet.
            </Text>
        );
    }

    return (
        <div
            id={"information-request-template-list"}
            className={styles.list}
        >
            {templates.map(template => (
                <div
                    id={`information-request-template-card-${template.id}`}
                    key={template.id}
                    className={styles.card}
                >
                    <div className={styles.cardMain}>
                        <Text weight={"semibold"}>{template.displayName}</Text>
                        <Text className={styles.keyText}>
                            {template.namespace}:{template.templateKey}
                        </Text>
                        <Badge
                            appearance={"tint"}
                            color={template.status === InformationRequestTemplateStatus.PUBLISHED ? "success" : "warning"}
                            size={"small"}
                        >
                            {template.status === InformationRequestTemplateStatus.PUBLISHED ? "Published" : "Draft"}
                        </Badge>
                    </div>
                    {canManage && template.hasEditableVersion && (
                        <Button
                            id={`information-request-template-open-draft-${template.id}`}
                            appearance={"subtle"}
                            shape={"circular"}
                            onClick={() => onOpenDraft(template)}
                        >
                            Open draft
                        </Button>
                    )}
                </div>
            ))}
        </div>
    );
};

export default InformationRequestTemplateList;
