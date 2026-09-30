import {Badge, Button, MessageBar, MessageBarBody, Spinner, Text} from "@fluentui/react-components";
import {InformationRequestTemplateStatus, InformationRequestTemplateSummaryDto} from "../../../models/models.tsx";
import {useTemplateListStyles} from "./TemplateListStyles.tsx";

interface TemplateListProps
{
    templates: InformationRequestTemplateSummaryDto[];
    loading: boolean;
    error: string | null;
    onOpen: (template: InformationRequestTemplateSummaryDto) => void;
}

const statusText: Record<InformationRequestTemplateStatus, string> = {
    [InformationRequestTemplateStatus.DRAFT]: "Draft",
    [InformationRequestTemplateStatus.PUBLISHED]: "Published",
    [InformationRequestTemplateStatus.RETIRED]: "Retired",
};

const TemplateList = ({templates, loading, error, onOpen}: TemplateListProps) =>
{
    const styles = useTemplateListStyles();

    if (loading && templates.length === 0)
    {
        return (
            <Spinner id={"information-request-template-list-loading"}
                     size={"small"}
                     label={"Loading Templates"}/>
        );
    }
    if (error)
    {
        return (
            <MessageBar id={"information-request-template-list-error"}
                        intent={"error"}>
                <MessageBarBody>{error}</MessageBarBody>
            </MessageBar>
        );
    }
    if (templates.length === 0)
    {
        return (
            <Text id={"information-request-template-list-empty"}
                  className={styles.muted}>
                No Information Request Templates in this scope yet.
            </Text>
        );
    }

    return (
        <ul id={"information-request-template-list"}
            aria-label={"Information Request Templates"}
            className={styles.list}>
            {templates.map(template => (
                <li key={template.id}
                    id={`information-request-template-card-${template.id}`}
                    className={styles.card}>
                    <div id={`information-request-template-card-${template.id}-main`}
                         className={styles.main}>
                        <Text id={`information-request-template-card-${template.id}-name`}
                              weight={"semibold"}>
                            {template.displayName}
                        </Text>
                        {template.description && (
                            <Text id={`information-request-template-card-${template.id}-description`}
                                  className={styles.muted}>
                                {template.description}
                            </Text>
                        )}
                        <div id={`information-request-template-card-${template.id}-badges`}
                             className={styles.badges}>
                            <Badge id={`information-request-template-card-${template.id}-status`}
                                   appearance={"tint"}
                                   color={template.status === InformationRequestTemplateStatus.PUBLISHED ? "success" : "warning"}>
                                {statusText[template.status]}
                            </Badge>
                            {template.latestPublishedVersionNumber && (
                                <Badge id={`information-request-template-card-${template.id}-published`}
                                       appearance={"outline"}
                                       color={"informative"}>
                                    {`Version ${template.latestPublishedVersionNumber} published`}
                                </Badge>
                            )}
                            {template.hasEditableVersion && (
                                <Badge id={`information-request-template-card-${template.id}-draft`}
                                       appearance={"outline"}
                                       color={"warning"}>
                                    Draft open
                                </Badge>
                            )}
                        </div>
                    </div>
                    <Button id={`information-request-template-open-${template.id}`}
                            appearance={"secondary"}
                            shape={"circular"}
                            aria-label={`Open ${template.displayName}`}
                            onClick={() => onOpen(template)}>
                        Open
                    </Button>
                </li>
            ))}
        </ul>
    );
};

export default TemplateList;
