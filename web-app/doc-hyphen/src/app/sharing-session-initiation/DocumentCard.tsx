import React from 'react';
import {Button, Card, Dropdown, Field, Input, Option, OptionGroup, Switch} from "@fluentui/react-components";
import {DeleteRegular} from "@fluentui/react-icons";
import {DocumentType, ImageType, SharingSessionRequestDocumentRequest} from "../models/models.tsx";

interface DocumentCardProps
{
    document: SharingSessionRequestDocumentRequest;
    index: number;
    onDocumentNameChange: (index: number, newValue: string) => void;
    onDocumentTypeChange: (index: number, newType: DocumentType | ImageType) => void;
    onRestrictDocumentTypeChange: (index: number, ev: React.ChangeEvent<HTMLInputElement>) => void;
    onDeleteDocument: (index: number) => void;
}

const DocumentCard: React.FC<DocumentCardProps> = ({
                                                       document,
                                                       index,
                                                       onDocumentNameChange,
                                                       onDocumentTypeChange,
                                                       onRestrictDocumentTypeChange,
                                                       onDeleteDocument
                                                   }) => (
    <Card key={index} className="shading-session-document-card">
        <div>
            <div id={"shading-session-document-card-header"}>
                <Field className={"field"}>
                    <Input type="text"
                           appearance={"underline"}
                           size={"small"}
                           value={document.title || ''}
                           required
                           onChange={(e) => onDocumentNameChange(index, e.target.value)}
                           placeholder={"Document name"}
                    />
                </Field>
                <Button icon={<DeleteRegular/>}
                        appearance={"subtle"}
                        onClick={() => onDeleteDocument(index)}/>
            </div>
            <div id={"shading-session-document-card-doc-type"}>
                <Field label="">
                    <Switch
                        label={"Restrict type"}
                        checked={document.restrictType}
                        onChange={(ev) => onRestrictDocumentTypeChange(index, ev)}
                    />
                </Field>
                <Dropdown disabled={!document.restrictType}
                          appearance={"underline"}
                          size={"small"}
                          placeholder={"Select document type to restrict"}
                          onOptionSelect={(_e, data) => onDocumentTypeChange(index, data.optionValue as any)}>
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
        </div>
    </Card>
);

export default DocumentCard;