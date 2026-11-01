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
import ExchangeAccessPanel from "../exchange-access-panel/ExchangeAccessPanel.tsx";
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
                id={"access-permissions-panel-accordion-1"}
                collapsible
                defaultOpenItems={["access-management"]}
            >
                <AccordionItem id={"access-permissions-panel-accordion-item-1"}
                               value={"access-management"}>
                    <AccordionHeader id={"access-permissions-panel-accordion-header-1"}>Access Management</AccordionHeader>
                    <AccordionPanel id={"access-permissions-panel-accordion-panel-1"}>
                        <ExchangeAccessPanel
                            exchangeId={props.exchangeId}
                            onAddPerson={props.onAddPerson}
                        />
                    </AccordionPanel>
                </AccordionItem>
                <AccordionItem id={"access-permissions-panel-accordion-item-2"}
                               value={"document-permissions"}>
                    <AccordionHeader id={"access-permissions-panel-accordion-header-2"}>Document permissions</AccordionHeader>
                    <AccordionPanel id={"access-permissions-panel-accordion-panel-2"}>
                        <div id={"access-permissions-panel-div-1"}
                             className={styles.switchGroup}>
                            <Field id={"access-permissions-panel-field-1"}>
                                <Switch
                                    id={"switch-allow-document-addition"}
                                    label={"Allow document additions"}
                                    checked={props.allowDocumentAddition}
                                    onChange={handleCheckboxChange(props.setAllowDocumentAddition)}
                                />
                            </Field>
                            <Field id={"access-permissions-panel-field-2"}>
                                <Switch
                                    id={"switch-allow-document-deletion"}
                                    label={"Allow document deletions"}
                                    checked={props.allowDocumentDeletion}
                                    onChange={handleCheckboxChange(props.setAllowDocumentDeletion)}
                                />
                            </Field>
                            <Field id={"access-permissions-panel-field-3"}>
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
                            <Field id={"access-permissions-panel-field-4"}>
                                <Switch
                                    id={"switch-allow-document-update"}
                                    label={"Allow document update"}
                                    checked={props.allowDocumentUpdate}
                                    onChange={handleCheckboxChange(props.setAllowDocumentUpdate)}
                                />
                            </Field>
                            <Field id={"access-permissions-panel-field-5"}>
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
