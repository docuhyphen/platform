import React from 'react';
import {Accordion, Divider, Text} from '@fluentui/react-components';
import {useManageAccessHelpGuideStyles} from './ManageAccessHelpGuideStyles.tsx';
import HelpGuideSection from './help-guide-section/HelpGuideSection.tsx';
import HelpGuideRoleCard from './help-guide-role-card/HelpGuideRoleCard.tsx';
import HelpGuideDetailCard from './help-guide-detail-card/HelpGuideDetailCard.tsx';
import {
    HELP_GUIDE_CONSTRAINTS,
    HELP_GUIDE_PERMISSIONS,
    HELP_GUIDE_ROLES,
    HELP_GUIDE_SETTINGS,
} from './manageAccessHelpGuideContent.ts';

const ManageAccessHelpGuide: React.FC = () =>
{
    const styles = useManageAccessHelpGuideStyles();

    return (
        <div id={"manage-access-help-guide-div-1"}
             className={styles.container}>
            <Text id={"manage-access-help-guide-text-1"}
                  size={200}
                  className={styles.intro}>
                This guide explains how Exchange access works, including roles, constraints,
                and exchange settings.
            </Text>

            <Accordion id={"manage-access-help-guide-accordion-1"}
                       multiple
                       collapsible
                       defaultOpenItems={['roles']}>
                <HelpGuideSection index={1}
                                  value="roles"
                                  title="Roles"
                                  sectionId={"manage-access-help-guide-div-2"}
                                  introId={"manage-access-help-guide-text-2"}
                                  intro={<>
                                      Each person in an Exchange is assigned a role that determines what they can do.
                                      The exchange creator is always the <strong id={"manage-access-help-guide-strong-1"}>Owner</strong>.
                                  </>}>
                    {HELP_GUIDE_ROLES.map((role) => (
                        <HelpGuideRoleCard key={role.cardId}
                                           role={role}/>
                    ))}
                </HelpGuideSection>

                <HelpGuideSection index={2}
                                  value="constraints"
                                  title="Constraints"
                                  sectionId={"manage-access-help-guide-div-17"}
                                  introId={"manage-access-help-guide-text-10"}
                                  intro={<>
                                      Constraints are additional restrictions you can apply to <strong id={"manage-access-help-guide-strong-2"}>Viewer</strong> and <strong id={"manage-access-help-guide-strong-3"}>Participant</strong> roles
                                      to control how they interact with documents.
                                  </>}>
                    {HELP_GUIDE_CONSTRAINTS.map((card) => (
                        <HelpGuideDetailCard key={card.cardId}
                                             card={card}/>
                    ))}
                </HelpGuideSection>

                <HelpGuideSection index={3}
                                  value="permissions"
                                  title="Document permissions"
                                  sectionId={"manage-access-help-guide-div-23"}
                                  introId={"manage-access-help-guide-text-21"}
                                  intro={<>
                                      Document permissions are exchange-wide settings that control what actions
                                      recipients and participants can perform on documents.
                                  </>}>
                    {HELP_GUIDE_PERMISSIONS.map((card) => (
                        <HelpGuideDetailCard key={card.cardId}
                                             card={card}/>
                    ))}
                </HelpGuideSection>

                <HelpGuideSection index={4}
                                  value="settings"
                                  title="Exchange settings"
                                  sectionId={"manage-access-help-guide-div-29"}>
                    {HELP_GUIDE_SETTINGS.map((card) => (
                        <HelpGuideDetailCard key={card.cardId}
                                             card={card}/>
                    ))}
                </HelpGuideSection>

                <HelpGuideSection index={5}
                                  value="summary"
                                  title="Summary tab"
                                  sectionId={"manage-access-help-guide-div-33"}>
                    <Text id={"manage-access-help-guide-text-38"}
                          size={200}>
                        The Summary tab shows all people involved in the Exchange: the <strong id={"manage-access-help-guide-strong-4"}>Requester</strong> (who
                        initiated the Exchange), the <strong id={"manage-access-help-guide-strong-5"}>Primary recipient</strong> (the main person the
                        exchange was shared with), and any additional <strong id={"manage-access-help-guide-strong-6"}>Participants</strong> (users or
                        groups added later through the Access &amp; permissions tab).
                    </Text>
                </HelpGuideSection>
            </Accordion>

            <Divider id={"manage-access-help-guide-divider-1"}
                     className={styles.footerDivider}/>
            <Text id={"manage-access-help-guide-text-39"}
                  size={200}
                  className={styles.footerNote}>
                Changes to access and permissions take effect immediately after saving. Protected entries
                (your own access and the exchange owner) cannot be modified or revoked.
            </Text>
        </div>
    );
};

export default ManageAccessHelpGuide;
