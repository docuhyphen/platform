import React from 'react';
import {Dropdown, Field, InfoLabel, Option, Text} from "@fluentui/react-components";
import {useRecipientRoleSelectorStyles} from "./RecipientRoleSelectorStyles.tsx";
import {
    ASSIGNABLE_ROLES,
    CONSTRAINED_ROLES,
    ExchangeShareRoleName,
    ExchangeShareRoleDisplayNames,
} from '../../../../../services/types/roles.ts';
import {ShareConstraints} from '../../../../../services/types/dtos.ts';
import ShareConstraintToggles from '../../../../components/share-constraints/ShareConstraintToggles.tsx';

interface RecipientRoleSelectorProps
{
    recipientRole: ExchangeShareRoleName | undefined;
    setRecipientRole: (role: ExchangeShareRoleName | undefined) => void;
    recipientConstraints: ShareConstraints;
    setRecipientConstraints: (c: ShareConstraints) => void;
}

const roleDescriptions: { role: string; description: string }[] = [
    {role: "Auto", description: "derived from document permissions (Editor if write access granted, Viewer otherwise)."},
    {role: "Editor", description: "can add, update, upload, and manage documents."},
    {role: "Viewer", description: "read-only access. Supports download and watermark constraints."},
    {role: "Participant", description: "flexible read access with optional constraints."},
    {role: "Commenter", description: "can view documents and leave comments."},
    {role: "Reviewer", description: "can view and comment, typically for approval workflows."},
    {role: "Signer", description: "read access plus formal signing capabilities."},
];

const RecipientRoleSelector: React.FC<RecipientRoleSelectorProps> = (props) =>
{
    const styles = useRecipientRoleSelectorStyles();

    return (
        <div className={styles.roleSection}>
            <Field
                id={"exchange-recipient-role-field"}
                label={
                    <InfoLabel
                        info={
                            <div className={styles.roleInfoRow}>
                                <Text size={200}>Each role determines what the recipient can do in this
                                    exchange.</Text>
                                {roleDescriptions.map((r) => (
                                    <div key={r.role}>
                                        <Text size={200} weight="semibold">{r.role}</Text>{' '}
                                        <Text size={200}>{r.description}</Text>
                                    </div>
                                ))}
                            </div>
                        }
                    >
                        Recipient role
                    </InfoLabel>
                }
                hint="Defaults to Editor/Viewer based on document permissions."
            >
                <Dropdown
                    id={"exchange-recipient-role-dropdown"}
                    size="small"
                    value={props.recipientRole ? ExchangeShareRoleDisplayNames[props.recipientRole] : 'Auto'}
                    selectedOptions={props.recipientRole ? [props.recipientRole] : ['AUTO']}
                    onOptionSelect={(_e, data) =>
                    {
                        const v = data.optionValue;
                        if (!v || v === 'AUTO')
                        {
                            props.setRecipientRole(undefined);
                            return;
                        }
                        props.setRecipientRole(v as ExchangeShareRoleName);
                    }}
                >
                    <Option id={"exchange-recipient-role-option-auto"}
                            value="AUTO"
                            text="Auto">Auto (derive from document permissions)</Option>
                    {[...ASSIGNABLE_ROLES].map((r) => (
                        <Option id={`exchange-recipient-role-option-${r}`}
                                key={r}
                                value={r}
                                text={ExchangeShareRoleDisplayNames[r]}>
                            {ExchangeShareRoleDisplayNames[r]}
                        </Option>
                    ))}
                </Dropdown>
            </Field>

            {props.recipientRole && CONSTRAINED_ROLES.has(props.recipientRole) && (
                <div className={styles.constraintsRow}>
                    <ShareConstraintToggles
                        constraints={props.recipientConstraints}
                        onChange={props.setRecipientConstraints}
                    />
                </div>
            )}
        </div>
    );
};

export default RecipientRoleSelector;
