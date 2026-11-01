import React from 'react';
import {Button, Field, Input, Radio, RadioGroup, Spinner, Textarea} from '@fluentui/react-components';
import {
    BlueprintDocumentConfig,
    BlueprintFieldDefaultConfig,
    BlueprintParticipantConfig,
} from '../../../models/models.tsx';
import {useExchangeInitiationStyles} from '../../ExchangeInitiationStyles.tsx';
import {SaveTarget, useSaveBlueprint} from './useSaveBlueprint.ts';
import BlueprintTagEditor from './blueprint-tag-editor/BlueprintTagEditor.tsx';

interface SaveBlueprintPanelProps
{
    onSaved: () => void;
    initialName: string;
    configJson: string;
    exchangeDocuments: BlueprintDocumentConfig[];
    participants: BlueprintParticipantConfig[];
    schemaDefinitionId?: string;
    fieldDefaults?: BlueprintFieldDefaultConfig[];
}

const SaveBlueprintPanel: React.FC<SaveBlueprintPanelProps> = (props) =>
{
    const styles = useExchangeInitiationStyles();
    const {
        isAdmin,
        name,
        setName,
        summary,
        setSummary,
        tags,
        addTag,
        removeTag,
        saveTarget,
        setSaveTarget,
        saving,
        error,
        save,
    } = useSaveBlueprint(props);

    return (
        <div id={"save-blueprint-dialog-div-1"}
             className={styles.saveBlueprintContainer}>
            {isAdmin && (
                <Field id={"save-blueprint-dialog-field-1"}
                       label="Save to">
                    <RadioGroup
                        id={"save-blueprint-target-group"}
                        value={saveTarget}
                        onChange={(_, d) => setSaveTarget(d.value as SaveTarget)}
                        layout="horizontal"
                    >
                        <Radio id={"save-blueprint-dialog-radio-1"}
                               value="PERSONAL"
                               label="My Blueprints"/>
                        <Radio id={"save-blueprint-dialog-radio-2"}
                               value="ORG"
                               label="Organization"/>
                    </RadioGroup>
                </Field>
            )}
            <Field id={"save-blueprint-dialog-field-2"}
                   label="Name"
                   required>
                <Input
                    id={"save-blueprint-name-input"}
                    value={name}
                    onChange={(_, d) => setName(d.value)}
                    placeholder="Blueprint name"
                    autoFocus
                />
            </Field>
            <Field id={"save-blueprint-dialog-field-3"}
                   label="Summary">
                <Textarea
                    id={"save-blueprint-summary-textarea"}
                    value={summary}
                    onChange={(_, d) => setSummary(d.value)}
                    placeholder="Short description of this blueprint"
                    rows={2}
                />
            </Field>
            <Field id={"save-blueprint-dialog-field-4"}
                   label="Tags">
                <BlueprintTagEditor
                    tags={tags}
                    onAddTag={addTag}
                    onRemoveTag={removeTag}
                />
            </Field>
            {error && (
                <span id={"save-blueprint-dialog-span-1"}
                      className={styles.saveBlueprintErrorText}>
                    {error}
                </span>
            )}
            <div id={"save-blueprint-dialog-div-3"}
                 className={styles.saveBlueprintActions}>
                <Button
                    id={"save-blueprint-save-btn"}
                    appearance="primary"
                    shape="circular"
                    onClick={save}
                    disabled={saving}
                >
                    {saving ? <><Spinner id={"save-blueprint-dialog-spinner-1"}
                                         size="tiny"/> Saving...</> : 'Save Blueprint'}
                </Button>
            </div>
        </div>
    );
};

export default SaveBlueprintPanel;
