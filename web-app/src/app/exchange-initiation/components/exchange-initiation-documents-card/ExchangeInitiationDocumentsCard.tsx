import React from 'react';
import {Badge, Button, Card, Checkbox, Dropdown, Field, Input, Option, OptionGroup, Switch} from "@fluentui/react-components";
import {AvailableVariablesDto, DocumentType, ExchangeRequestDocumentRequest, ImageType} from "../../../models/models.tsx";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {DeleteIcon, LinkDismissIcon} from "../../../components/IconBundles.tsx";
import VariableTokenInput from "../../../../components/variable-token-input/VariableTokenInput.tsx";

interface DocumentCardProps
{
    document: ExchangeRequestDocumentRequest;
    index: number;
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: DocumentType | ImageType) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
    onRequiredChange: (index: number, required: boolean) => void;
    onUnlink?: (index: number) => void;
    availableVariables?: AvailableVariablesDto;
    locked?: boolean;
}

const ExchangeInitiationDocumentsCard: React.FC<DocumentCardProps> = (
    {
        document,
        index,
        onDocumentNameChange,
        onDocumentTypeChange,
        onRestrictDocumentTypeChange,
        onDeleteDocument,
        onRequiredChange,
        onUnlink,
        availableVariables,
        locked,
    }) =>
{
    const styles = useExchangeInitiationStyles();
    const isLinked = !!document.libraryDocumentId;

    return (
        <Card key={index} className={styles.shadingExchangeDocumentCard}>
            <div>
                <div className={styles.dialogTitle1}>
                    <Field className={styles.sharingDetailsInput}>
                        {availableVariables ? (
                            <VariableTokenInput
                                value={document.title || ''}
                                onChange={v => onDocumentNameChange(index, v)}
                                availableVariables={availableVariables}
                                placeholder="Document name, type {{ to insert a variable"
                                disabled={isLinked || locked}
                            />
                        ) : (
                            <Input
                                id={`doc-card-name-input-${index}`}
                                type="text"
                                size="small"
                                value={document.title || ''}
                                required
                                onChange={(e) => onDocumentNameChange(index, e.target.value)}
                                placeholder="Document name"
                                disabled={isLinked || locked}
                            />
                        )}
                    </Field>
                    {!locked && (
                        <Button
                            id={`doc-card-delete-btn-${index}`}
                            icon={<DeleteIcon className={styles.iconDeleteFilled}/>}
                            appearance="transparent"
                            shape={"circular"}
                            onClick={() => onDeleteDocument(index)}
                        />
                    )}
                </div>
                {isLinked && (
                    <div className={styles.docLinkedBadgeRow}>
                        <Badge appearance="tint" color="success" size="small">
                            Linked from library
                        </Badge>
                        {onUnlink && (
                            <Button
                                id={`doc-card-unlink-btn-${index}`}
                                size="small"
                                appearance="outline"
                                shape="circular"
                                icon={<LinkDismissIcon/>}
                                onClick={() => onUnlink(index)}
                            >
                                Unlink
                            </Button>
                        )}
                    </div>
                )}
                <div className={styles.exchangeDocumentsRestriction}>
                    <div className={styles.exchangeDocumentsRestrictionField}>
                        <Field label="">
                            <Switch
                                id={`doc-card-restrict-switch-${index}`}
                                label="Restrict upload type"
                                checked={document.restrictType ?? false}
                                disabled={isLinked || locked}
                                onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                            />
                        </Field>
                        <Dropdown
                            id={`doc-card-type-dropdown-${index}`}
                            className={styles.exchangeDocumentsDropdown}
                            disabled={!document.restrictType || isLinked || locked}
                            appearance="underline"
                            value={document.restrictedType ?? ''}
                            size="small"
                            placeholder="Select allowed upload type"
                            onOptionSelect={(_e, data) => onDocumentTypeChange(index, data.optionValue as any)}
                        >
                            <OptionGroup label="Documents">
                                {Object.values(DocumentType).map((option) => (
                                    <Option key={option} value={option}>
                                        {option}
                                    </Option>
                                ))}
                            </OptionGroup>
                            <OptionGroup label="Images">
                                {Object.values(ImageType).map((option) => (
                                    <Option key={option} value={option}>
                                        {option}
                                    </Option>
                                ))}
                            </OptionGroup>
                        </Dropdown>
                    </div>
                    <Checkbox
                        id={`doc-card-required-checkbox-${index}`}
                        label="Required"
                        checked={document.required ?? false}
                        disabled={isLinked || locked}
                        onChange={(_, d) => onRequiredChange(index, !!d.checked)}
                    />
                </div>
            </div>
        </Card>
    );
};

export default ExchangeInitiationDocumentsCard;
