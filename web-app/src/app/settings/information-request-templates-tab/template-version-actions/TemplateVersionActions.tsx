import {Button} from "@fluentui/react-components";
import {AddIcon, CopyIcon, DeleteIcon} from "../../../components/IconBundles.tsx";
import {useTemplateVersionActionsStyles} from "./TemplateVersionActionsStyles.tsx";

interface TemplateVersionActionsProps {
    versionNumber: number;
    canManage: boolean;
    canStartDraft: boolean;
    canCopy: boolean;
    busy: boolean;
    onStartDraft: () => void;
    onRetire: () => void;
    onCopy: () => void;
}

const TemplateVersionActions = ({
                                    versionNumber,
                                    canManage,
                                    canStartDraft,
                                    canCopy,
                                    busy,
                                    onStartDraft,
                                    onRetire,
                                    onCopy,
                                }: TemplateVersionActionsProps) => {
    const styles = useTemplateVersionActionsStyles();

    return (
        <div id={"information-request-template-versions-actions"}
             className={styles.actions}>
            {canManage && canStartDraft && (
                <Button id={"information-request-template-start-draft"}
                        appearance={"primary"}
                        shape={"circular"}
                        icon={<AddIcon/>}
                        disabled={busy}
                        onClick={onStartDraft}>
                    {`Start a new draft from version ${versionNumber}`}
                </Button>
            )}
            {canManage && (
                <Button id={"information-request-template-retire"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<DeleteIcon/>}
                        disabled={busy}
                        onClick={onRetire}>
                    {`Retire version ${versionNumber}`}
                </Button>
            )}
            {canCopy && (
                <Button id={"information-request-template-clone"}
                        appearance={"secondary"}
                        shape={"circular"}
                        icon={<CopyIcon/>}
                        disabled={busy}
                        onClick={onCopy}>
                    Copy as a new Template
                </Button>
            )}
        </div>
    );
};

export default TemplateVersionActions;
