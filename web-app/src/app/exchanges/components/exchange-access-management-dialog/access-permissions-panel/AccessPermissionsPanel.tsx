import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Field,
    Switch,
} from "@fluentui/react-components";
import {Dispatch, SetStateAction} from "react";
import {handleCheckboxChange} from "../../../../exchange-initiation/formHandlers.tsx";
import DownloadFormatRestriction from "../../../../components/share-constraints/DownloadFormatRestriction.tsx";
import ExchangeAccessPanel from "../ExchangeAccessPanel.tsx";
import {useAccessPermissionsPanelStyles} from "./AccessPermissionsPanelStyles.tsx";

interface AccessPermissionsPanelProps
{
    exchangeId: string;
    allowDocumentAddition: boolean;
    setAllowDocumentAddition: Dispatch<SetStateAction<boolean>>;
    allowDocumentDeletion: boolean;
    setAllowDocumentDeletion: Dispatch<SetStateAction<boolean>>;
    allowDocumentDownload: boolean;
    setAllowDocumentDownload: Dispatch<SetStateAction<boolean>>;
    allowDocumentUpdate: boolean;
    setAllowDocumentUpdate: Dispatch<SetStateAction<boolean>>;
    allowDocumentUpload: boolean;
    setAllowDocumentUpload: Dispatch<SetStateAction<boolean>>;
    allowedDownloadFormats?: string[];
    setAllowedDownloadFormats: Dispatch<SetStateAction<string[] | undefined>>;
    onAddPerson: () => void;
}

const AccessPermissionsPanel = (props: AccessPermissionsPanelProps) =>
{
    const styles = useAccessPermissionsPanelStyles();
    return (
        <section id={"access-mgmt-permissions"}>
            <Accordion
                collapsible
                defaultOpenItems={["access-management"]}
            >
                <AccordionItem value={"access-management"}>
                    <AccordionHeader>Access Management</AccordionHeader>
                    <AccordionPanel>
                        <ExchangeAccessPanel
                            exchangeId={props.exchangeId}
                            onAddPerson={props.onAddPerson}
                        />
                    </AccordionPanel>
                </AccordionItem>
                <AccordionItem value={"document-permissions"}>
                    <AccordionHeader>Document permissions</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.switchGroup}>
                            <Field>
                                <Switch
                                    id={"switch-allow-document-addition"}
                                    label={"Allow document additions"}
                                    checked={props.allowDocumentAddition}
                                    onChange={handleCheckboxChange(props.setAllowDocumentAddition)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    id={"switch-allow-document-deletion"}
                                    label={"Allow document deletions"}
                                    checked={props.allowDocumentDeletion}
                                    onChange={handleCheckboxChange(props.setAllowDocumentDeletion)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    id={"switch-allow-document-download"}
                                    label={"Allow document download"}
                                    checked={props.allowDocumentDownload}
                                    onChange={handleCheckboxChange(props.setAllowDocumentDownload)}
                                />
                            </Field>
                            {props.allowDocumentDownload && (
                                <DownloadFormatRestriction
                                    allowedDownloadFormats={props.allowedDownloadFormats}
                                    onChange={props.setAllowedDownloadFormats}
                                />
                            )}
                            <Field>
                                <Switch
                                    id={"switch-allow-document-update"}
                                    label={"Allow document update"}
                                    checked={props.allowDocumentUpdate}
                                    onChange={handleCheckboxChange(props.setAllowDocumentUpdate)}
                                />
                            </Field>
                            <Field>
                                <Switch
                                    id={"switch-allow-document-upload"}
                                    label={"Allow document upload"}
                                    checked={props.allowDocumentUpload}
                                    onChange={handleCheckboxChange(props.setAllowDocumentUpload)}
                                />
                            </Field>
                        </div>
                    </AccordionPanel>
                </AccordionItem>
            </Accordion>
        </section>
    );
};

export default AccessPermissionsPanel;
