import React from 'react';
import {Badge, Text} from '@fluentui/react-components';
import {HelpGuideRoleContent} from '../manageAccessHelpGuideContent.ts';
import {useHelpGuideRoleCardStyles} from './HelpGuideRoleCardStyles.tsx';

interface HelpGuideRoleCardProps
{
    role: HelpGuideRoleContent;
}

const HelpGuideRoleCard: React.FC<HelpGuideRoleCardProps> = ({role}) =>
{
    const styles = useHelpGuideRoleCardStyles();
    return (
        <div id={role.cardId}
             className={styles.roleCard}>
            <div id={role.headerId}
                 className={styles.roleHeader}>
                <Badge id={role.nameBadgeId}
                       appearance="filled"
                       color="brand">{role.name}</Badge>
                <Badge id={role.kindBadgeId}
                       appearance="outline"
                       color="subtle"
                       size="small">{role.kind}</Badge>
            </div>
            <Text id={role.textId}
                  size={200}>
                {role.description}
            </Text>
        </div>
    );
};

export default HelpGuideRoleCard;
