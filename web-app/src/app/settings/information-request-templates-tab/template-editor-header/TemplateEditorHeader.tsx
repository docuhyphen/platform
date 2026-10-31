import {Badge, Button, Text} from "@fluentui/react-components";
import {BackIcon} from "../../../components/IconBundles.tsx";
import {
    InformationRequestTemplateDto,
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionStandingDto,
} from "../../../models/models.tsx";
import {useTemplateEditorHeaderStyles} from "./TemplateEditorHeaderStyles.tsx";

interface TemplateEditorHeaderProps
{
    template: InformationRequestTemplateDto;
    readOnly: boolean;
    shown?: InformationRequestTemplateVersionStandingDto | null;
    onShowCurrent?: () => void;
    onBack: () => void;
}

const versionText = (
    template: InformationRequestTemplateDto,
    shown: InformationRequestTemplateVersionStandingDto | null | undefined,
): string => {
    if (shown) {
        if (!template.draftVersion && !template.latestPublishedVersion) {
            return `All versions retired, viewing version ${shown.versionNumber}`;
        }
        const standing = shown.status === InformationRequestTemplateStatus.RETIRED ? "retired" : "published";
        return `Viewing version ${shown.versionNumber}, ${standing}`;
    }
    if (template.draftVersion) return `Draft version ${template.draftVersion.versionNumber}`;
    if (template.latestPublishedVersion) return `Published version ${template.latestPublishedVersion.versionNumber}`;
    if (template.versions?.some(version => version.status === InformationRequestTemplateStatus.RETIRED)) return "All versions retired";
    return "No version yet";
};

const versionColor = (
    template: InformationRequestTemplateDto,
    shown: InformationRequestTemplateVersionStandingDto | null | undefined,
): "informative" | "warning" | "success" => {
    if (shown) return "informative";
    return template.draftVersion ? "warning" : "success";
};

const TemplateEditorHeader = ({template, readOnly, shown, onShowCurrent, onBack}: TemplateEditorHeaderProps) =>
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
                       color={versionColor(template, shown)}>
                    {versionText(template, shown)}
                </Badge>
                {readOnly && (
                    <Badge id={"information-request-template-editor-read-only"}
                           appearance={"outline"}
                           color={"informative"}>
                        Read only
                    </Badge>
                )}
                {onShowCurrent && (
                    <Button id={"information-request-template-editor-show-current"}
                            appearance={"subtle"}
                            shape={"circular"}
                            size={"small"}
                            onClick={onShowCurrent}>
                        Show current version
                    </Button>
                )}
            </div>
        </header>
    );
};

export default TemplateEditorHeader;
