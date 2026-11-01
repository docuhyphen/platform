import React from 'react';
import {Button, Card, Checkbox, Field, Input, Switch} from "@fluentui/react-components";
import {AvailableVariablesDto, DocumentType, ExchangeRequestDocumentRequest, ImageType} from "../../../models/models.tsx";
import {useExchangeInitiationStyles} from "../../ExchangeInitiationStyles.tsx";
import {DeleteIcon} from "../../../components/IconBundles.tsx";
import VariableTokenInput from "../../../../components/variable-token-input/VariableTokenInput.tsx";
import DocumentLinkedBadgeRow from "./document-linked-badge-row/DocumentLinkedBadgeRow.tsx";
import DocumentTypeDropdown from "./document-type-dropdown/DocumentTypeDropdown.tsx";

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
        <Card id={`exchange-initiation-documents-card-card-1-${index}`}
              key={index}
              className={styles.shadingExchangeDocumentCard}>
            <div id={"exchange-initiation-documents-card-div-1"}>
                <div id={"exchange-initiation-documents-card-div-2"}
                     className={styles.dialogTitle1}>
                    <Field id={"exchange-initiation-documents-card-field-1"}
                           className={styles.sharingDetailsInput}>
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
                    <DocumentLinkedBadgeRow index={index}
                                            onUnlink={onUnlink}/>
                )}
                <div id={"exchange-initiation-documents-card-div-4"}
                     className={styles.exchangeDocumentsRestriction}>
                    <div id={"exchange-initiation-documents-card-div-5"}
                         className={styles.exchangeDocumentsRestrictionField}>
                        <Field id={"exchange-initiation-documents-card-field-2"}
                               label="">
                            <Switch
                                id={`doc-card-restrict-switch-${index}`}
                                label="Restrict upload type"
                                checked={document.restrictType ?? false}
                                disabled={isLinked || locked}
                                onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                            />
                        </Field>
                        <DocumentTypeDropdown
                            index={index}
                            value={document.restrictedType}
                            disabled={!document.restrictType || isLinked || locked}
                            onChange={(newType) => onDocumentTypeChange(index, newType)}
                        />
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
