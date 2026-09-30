import {useState} from "react";
import {Button, Text} from "@fluentui/react-components";
import {AddIcon, CopyIcon, DeleteIcon} from "../../../components/IconBundles.tsx";
import {
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
} from "../../../models/models.tsx";
import TemplateRetireDialog from "../template-retire-dialog/TemplateRetireDialog.tsx";
import TemplateCloneDialog from "../template-clone-dialog/TemplateCloneDialog.tsx";
import {useTemplateVersionsPanelStyles} from "./TemplateVersionsPanelStyles.tsx";

interface TemplateVersionsPanelProps
{
    template: InformationRequestTemplateDto;
    canManage: boolean;
    busy: boolean;
    onStartDraft: (versionNumber: number) => Promise<unknown>;
    onRetire: (versionNumber: number) => Promise<boolean>;
    onClone: (versionNumber: number, target: CreateInformationRequestTemplateRequest) => Promise<boolean>;
    copyTargets: InformationRequestTemplateScopeKind[];
}

const dateText = (value?: string): string => value ? new Date(value).toLocaleDateString() : "";

const TemplateVersionsPanel = ({template, canManage, copyTargets, busy, onStartDraft, onRetire, onClone}: TemplateVersionsPanelProps) =>
{
    const styles = useTemplateVersionsPanelStyles();
    const [dialog, setDialog] = useState<"retire" | "clone" | null>(null);
    const published = template.latestPublishedVersion;
    const draft = template.draftVersion;
    const copySource = published?.versionNumber ?? draft?.versionNumber;
    const retirable = published?.status === InformationRequestTemplateStatus.PUBLISHED;

    return (
        <div id={"information-request-template-versions"}
             className={styles.panel}>
            {draft && (
                <Text id={"information-request-template-versions-draft"}>
                    {`Draft version ${draft.versionNumber} is open for editing. Publishing freezes it; a published version never changes.`}
                </Text>
            )}
            {published && (
                <Text id={"information-request-template-versions-published"}>
                    {`${published.status === InformationRequestTemplateStatus.RETIRED ? "Retired" : "Published"} version ${published.versionNumber}`}
                </Text>
            )}
            {published?.publishedAt && (
                <Text id={"information-request-template-versions-published-at"}
                      className={styles.muted}>
                    {`Published on ${dateText(published.publishedAt)}. Requests already created keep the version they started with.`}
                </Text>
            )}
            {(canManage || copyTargets.length > 0) && (
                <div id={"information-request-template-versions-actions"}
                     className={styles.actions}>
                    {canManage && published && !draft && (
                        <Button id={"information-request-template-start-draft"}
                                appearance={"primary"}
                                shape={"circular"}
                                icon={<AddIcon/>}
                                disabled={busy}
                                onClick={() => void onStartDraft(published.versionNumber)}>
                            {`Start a new draft from version ${published.versionNumber}`}
                        </Button>
                    )}
                    {canManage && published && retirable && (
                        <Button id={"information-request-template-retire"}
                                appearance={"secondary"}
                                shape={"circular"}
                                icon={<DeleteIcon/>}
                                disabled={busy}
                                onClick={() => setDialog("retire")}>
                            {`Retire version ${published.versionNumber}`}
                        </Button>
                    )}
                    {copySource !== undefined && copyTargets.length > 0 && (
                        <Button id={"information-request-template-clone"}
                                appearance={"secondary"}
                                shape={"circular"}
                                icon={<CopyIcon/>}
                                disabled={busy}
                                onClick={() => setDialog("clone")}>
                            Copy as a new Template
                        </Button>
                    )}
                </div>
            )}
            {dialog === "retire" && published && (
                <TemplateRetireDialog versionNumber={published.versionNumber}
                                      busy={busy}
                                      onConfirm={async () =>
                                      {
                                          if (await onRetire(published.versionNumber)) setDialog(null);
                                      }}
                                      onDismiss={() => setDialog(null)}/>
            )}
            {dialog === "clone" && copySource !== undefined && (
                <TemplateCloneDialog template={template}
                                     targets={copyTargets}
                                     busy={busy}
                                     onConfirm={async target =>
                                     {
                                         if (await onClone(copySource, target)) setDialog(null);
                                     }}
                                     onDismiss={() => setDialog(null)}/>
            )}
        </div>
    );
};

export default TemplateVersionsPanel;
