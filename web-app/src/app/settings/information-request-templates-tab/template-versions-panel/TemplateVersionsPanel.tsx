import {useState} from "react";
import {Text} from "@fluentui/react-components";
import {
    CreateInformationRequestTemplateRequest,
    InformationRequestTemplateDto,
    InformationRequestTemplateScopeKind,
    InformationRequestTemplateStatus,
    InformationRequestTemplateVersionStandingDto,
} from "../../../models/models.tsx";
import ChoiceSelect from "../../../information-requests/shared/choice-select/ChoiceSelect.tsx";
import {formatInformationRequestTime} from "../../../information-requests/shared/informationRequestFormatting.ts";
import TemplateRetireDialog from "../template-retire-dialog/TemplateRetireDialog.tsx";
import TemplateCloneDialog from "../template-clone-dialog/TemplateCloneDialog.tsx";
import TemplateVersionActions from "../template-version-actions/TemplateVersionActions.tsx";
import TemplateVersionList from "../template-version-list/TemplateVersionList.tsx";
import {useTemplateVersionsPanelStyles} from "./TemplateVersionsPanelStyles.tsx";

interface TemplateVersionsPanelProps
{
    template: InformationRequestTemplateDto;
    canManage: boolean;
    busy: boolean;
    shownVersionNumber: number | null;
    onView: (versionNumber: number) => void;
    onStartDraft: (versionNumber: number) => Promise<unknown>;
    onRetire: (versionNumber: number) => Promise<string | null>;
    onClone: (versionNumber: number, target: CreateInformationRequestTemplateRequest) => Promise<string | null>;
    copyTargets: InformationRequestTemplateScopeKind[];
}

const standingsOf = (template: InformationRequestTemplateDto): InformationRequestTemplateVersionStandingDto[] => {
    if (template.versions?.length) return template.versions;
    const published = template.latestPublishedVersion;
    return published
        ? [{versionNumber: published.versionNumber, status: published.status, publishedAt: published.publishedAt}]
        : [];
};

const TemplateVersionsPanel = ({
                                   template,
                                   canManage,
                                   copyTargets,
                                   busy,
                                   shownVersionNumber,
                                   onView,
                                   onStartDraft,
                                   onRetire,
                                   onClone,
                               }: TemplateVersionsPanelProps) =>
{
    const styles = useTemplateVersionsPanelStyles();
    const [dialog, setDialog] = useState<"retire" | "clone" | null>(null);
    const [dialogRefusal, setDialogRefusal] = useState<string | null>(null);
    const [picked, setPicked] = useState<number | null>(null);
    const published = template.latestPublishedVersion;
    const draft = template.draftVersion;
    const standings = standingsOf(template);
    const choices = standings
        .filter(version => version.status === InformationRequestTemplateStatus.PUBLISHED)
        .map(version => version.versionNumber);
    const chosen = picked !== null && choices.includes(picked) ? picked : published?.versionNumber;
    const acting = canManage || copyTargets.length > 0;
    const open = (next: "retire" | "clone" | null) => {
        setDialogRefusal(null);
        setDialog(next);
    };
    const settle = (refusal: string | null) => (refusal ? setDialogRefusal(refusal) : open(null));

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
                    {`Published version ${published.versionNumber}`}
                </Text>
            )}
            {published?.publishedAt && (
                <Text id={"information-request-template-versions-published-at"}
                      className={styles.muted}>
                    {`Published on ${formatInformationRequestTime(published.publishedAt)}. Requests already created keep the version they started with.`}
                </Text>
            )}
            {published?.contentHashSha256 && (
                <Text id={"information-request-template-versions-content-hash"}
                      className={styles.hash}>
                    {`Content hash ${published.contentHashSha256}`}
                </Text>
            )}
            {standings.length > 0 && (
                <TemplateVersionList versions={standings}
                                     shownVersionNumber={shownVersionNumber}
                                     onView={onView}/>
            )}
            {acting && choices.length > 1 && (
                <ChoiceSelect id={"information-request-template-version-choice"}
                              label={"Version to act on"}
                              value={chosen === undefined ? "" : String(chosen)}
                              options={choices.map(number => ({value: String(number), label: `Version ${number}`}))}
                              className={styles.choice}
                              onChange={value => setPicked(Number(value))}/>
            )}
            {acting && chosen !== undefined && (
                <TemplateVersionActions versionNumber={chosen}
                                        canManage={canManage}
                                        canStartDraft={!draft}
                                        canCopy={copyTargets.length > 0}
                                        busy={busy}
                                        onStartDraft={() => void onStartDraft(chosen)}
                                        onRetire={() => open("retire")}
                                        onCopy={() => open("clone")}/>
            )}
            {dialog === "retire" && chosen !== undefined && (
                <TemplateRetireDialog versionNumber={chosen}
                                      lastPublished={choices.length <= 1}
                                      draftVersionNumber={draft?.versionNumber}
                                      busy={busy}
                                      refusal={dialogRefusal}
                                      onConfirm={async () => settle(await onRetire(chosen))}
                                      onDismiss={() => open(null)}/>
            )}
            {dialog === "clone" && chosen !== undefined && (
                <TemplateCloneDialog template={template}
                                     targets={copyTargets}
                                     busy={busy}
                                     refusal={dialogRefusal}
                                     onConfirm={async target => settle(await onClone(chosen, target))}
                                     onDismiss={() => open(null)}/>
            )}
        </div>
    );
};

export default TemplateVersionsPanel;
