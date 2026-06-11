import React from 'react';
import {
    Accordion,
    AccordionHeader,
    AccordionItem,
    AccordionPanel,
    Badge,
    Divider,
    Text,
} from '@fluentui/react-components';
import {useManageAccessHelpGuideStyles} from './ManageAccessHelpGuideStyles.tsx';

const ManageAccessHelpGuide: React.FC = () =>
{
    const styles = useManageAccessHelpGuideStyles();

    return (
        <div className={styles.container}>
            <Text size={200} className={styles.intro}>
                This guide explains how exchange access works, including roles, constraints,
                and exchange settings.
            </Text>

            <Accordion multiple collapsible defaultOpenItems={['roles']}>
                <AccordionItem value="roles">
                    <AccordionHeader>Roles</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.section}>
                            <Text size={200} className={styles.sectionIntro}>
                                Each person in a exchange is assigned a role that determines what they can do.
                                The exchange creator is always the <strong>Owner</strong>.
                            </Text>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Owner</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Automatic</Badge>
                                </div>
                                <Text size={200}>
                                    Full control over the exchange. Can manage access, edit settings, add/remove
                                    documents, and end the exchange. This role is structural and cannot be assigned
                                    or revoked.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Editor</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable</Badge>
                                </div>
                                <Text size={200}>
                                    Can add, update, upload, and manage documents within the exchange. Editors
                                    have broad document-level access but cannot manage exchange access or settings.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Reviewer</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable</Badge>
                                </div>
                                <Text size={200}>
                                    Can view all documents and leave comments or notes. Reviewers are typically
                                    used for approval workflows or document review processes.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Signer</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable</Badge>
                                </div>
                                <Text size={200}>
                                    Intended for participants who need to sign or formally acknowledge documents.
                                    Has read access plus signing capabilities.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Viewer</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable · Supports constraints</Badge>
                                </div>
                                <Text size={200}>
                                    Read-only access to exchange documents. Viewers can browse and preview documents
                                    but cannot modify them. Supports additional constraints like download restrictions
                                    and watermarking.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Commenter</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable</Badge>
                                </div>
                                <Text size={200}>
                                    Can view documents and leave comments or notes, but cannot modify documents
                                    or exchange settings.
                                </Text>
                            </div>

                            <div className={styles.roleCard}>
                                <div className={styles.roleHeader}>
                                    <Badge appearance="filled" color="brand">Participant</Badge>
                                    <Badge appearance="outline" color="subtle" size="small">Assignable · Supports constraints</Badge>
                                </div>
                                <Text size={200}>
                                    General participant access. Can view documents with optional constraints
                                    applied. This is the most flexible role for external collaborators.
                                </Text>
                            </div>
                        </div>
                    </AccordionPanel>
                </AccordionItem>

                <AccordionItem value="constraints">
                    <AccordionHeader>Constraints</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.section}>
                            <Text size={200} className={styles.sectionIntro}>
                                Constraints are additional restrictions you can apply to <strong>Viewer</strong> and <strong>Participant</strong> roles
                                to control how they interact with documents.
                            </Text>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>No bulk document download</Text>
                                <Text size={200}>
                                    Prevents the participant from downloading documents in bulk (zip download).
                                    Documents can still be previewed in the browser.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>No reshare</Text>
                                <Text size={200}>
                                    Prevents the participant from sharing or forwarding the exchange link
                                    to others.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Watermark</Text>
                                <Text size={200}>
                                    Overlays a watermark on document previews to discourage unauthorized
                                    screenshots or distribution.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Require MFA</Text>
                                <Text size={200}>
                                    Requires the participant to complete multi-factor authentication before
                                    accessing exchange documents, adding an extra layer of security.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Max views</Text>
                                <Text size={200}>
                                    Limits the number of times a participant can view the exchange documents.
                                    Once the limit is reached, access is automatically revoked. Leave blank for
                                    unlimited views.
                                </Text>
                            </div>
                        </div>
                    </AccordionPanel>
                </AccordionItem>

                <AccordionItem value="permissions">
                    <AccordionHeader>Document permissions</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.section}>
                            <Text size={200} className={styles.sectionIntro}>
                                Document permissions are exchange-wide settings that control what actions
                                recipients and participants can perform on documents.
                            </Text>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Allow document additions</Text>
                                <Text size={200}>
                                    When enabled, participants with the appropriate role can add new documents
                                    to the exchange.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Allow document deletions</Text>
                                <Text size={200}>
                                    When enabled, participants can remove documents from the exchange.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Allow document zip download</Text>
                                <Text size={200}>
                                    When enabled, participants can download all exchange documents as a
                                    single zip archive.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Allow document update</Text>
                                <Text size={200}>
                                    When enabled, participants can modify or replace existing document metadata.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Allow document upload</Text>
                                <Text size={200}>
                                    When enabled, participants can upload new file versions for existing documents.
                                </Text>
                            </div>
                        </div>
                    </AccordionPanel>
                </AccordionItem>

                <AccordionItem value="settings">
                    <AccordionHeader>Exchange settings</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.section}>
                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Require recipient sign in</Text>
                                <Text size={200}>
                                    When enabled, the recipient must sign in with their account to access
                                    the exchange. When disabled, the recipient can use a one-time email
                                    access code (OTP) instead exch- useful for external parties who don't
                                    have an account.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>Send access code</Text>
                                <Text size={200}>
                                    When "Require recipient sign in" is disabled, you can send a one-time
                                    access code to the recipient's email. The code expires after the
                                    configured number of days.
                                </Text>
                            </div>

                            <div className={styles.constraintCard}>
                                <Text weight="semibold" size={300}>No-auth access validity</Text>
                                <Text size={200}>
                                    Sets how many days the one-time access code remains valid (1–30 days).
                                    After expiry, a new code must be sent.
                                </Text>
                            </div>
                        </div>
                    </AccordionPanel>
                </AccordionItem>

                <AccordionItem value="summary">
                    <AccordionHeader>Summary tab</AccordionHeader>
                    <AccordionPanel>
                        <div className={styles.section}>
                            <Text size={200}>
                                The Summary tab shows all people involved in the exchange: the <strong>Requester</strong> (who
                                initiated the exchange), the <strong>Primary recipient</strong> (the main person the
                                exchange was shared with), and any additional <strong>Participants</strong> (users or
                                groups added later through the Access &amp; permissions tab).
                            </Text>
                        </div>
                    </AccordionPanel>
                </AccordionItem>
            </Accordion>

            <Divider className={styles.footerDivider}/>
            <Text size={200} className={styles.footerNote}>
                Changes to access and permissions take effect immediately after saving. Protected entries
                (your own access and the exchange owner) cannot be modified or revoked.
            </Text>
        </div>
    );
};

export default ManageAccessHelpGuide;

