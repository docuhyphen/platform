import {Badge, Button, Text} from "@fluentui/react-components";
import {BackIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestTemplateDto} from "../../../models/models.tsx";
import {useTemplateEditorHeaderStyles} from "./TemplateEditorHeaderStyles.tsx";

interface TemplateEditorHeaderProps
{
    template: InformationRequestTemplateDto;
    readOnly: boolean;
    onBack: () => void;
}

const versionText = (template: InformationRequestTemplateDto): string =>
{
    if (template.draftVersion) return `Draft version ${template.draftVersion.versionNumber}`;
    if (template.latestPublishedVersion) return `Published version ${template.latestPublishedVersion.versionNumber}`;
    return "No version yet";
};

const TemplateEditorHeader = ({template, readOnly, onBack}: TemplateEditorHeaderProps) =>
{
    const styles = useTemplateEditorHeaderStyles();

    return (
        <header id={"information-request-template-editor-header"}
                className={styles.header}>
            <Button id={"information-request-template-editor-back"}
                    appearance={"subtle"}
                    shape={"circular"}
                    icon={<BackIcon/>}
                    onClick={onBack}>
                Back to Templates
            </Button>
            <div id={"information-request-template-editor-heading"}
                 className={styles.heading}>
                <Text id={"information-request-template-editor-title"}
                      as={"h2"}
                      size={500}
                      weight={"semibold"}
                      className={styles.title}>
                    {template.displayName}
                </Text>
                <Text id={"information-request-template-editor-key"}
                      className={styles.key}>
                    {`${template.namespace}:${template.templateKey}`}
                </Text>
                <Badge id={"information-request-template-editor-version"}
                       appearance={"tint"}
                       color={template.draftVersion ? "warning" : "success"}>
                    {versionText(template)}
                </Badge>
                {readOnly && (
                    <Badge id={"information-request-template-editor-read-only"}
                           appearance={"outline"}
                           color={"informative"}>
                        Read only
                    </Badge>
                )}
            </div>
        </header>
    );
};

export default TemplateEditorHeader;
