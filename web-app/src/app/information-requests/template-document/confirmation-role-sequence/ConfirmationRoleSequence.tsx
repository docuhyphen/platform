import {Button, Text} from "@fluentui/react-components";
import {ArrowUpIcon} from "../../../components/IconBundles.tsx";
import {InformationRequestContributorRole} from "../../../models/models.tsx";
import {contributorRoleLabels} from "../templateAuthoringLabels.ts";
import {useConfirmationRoleSequenceStyles} from "./ConfirmationRoleSequenceStyles.tsx";

type ConfirmingRole = keyof typeof contributorRoleLabels;

interface Props {
    id: string;
    roles: InformationRequestContributorRole[];
    disabled: boolean;
    onChange: (roles: InformationRequestContributorRole[]) => void;
}

const labelOf = (role: InformationRequestContributorRole): string =>
    contributorRoleLabels[role as ConfirmingRole] ?? role;

const ConfirmationRoleSequence = ({id, roles, disabled, onChange}: Props) => {
    const styles = useConfirmationRoleSequenceStyles();
    const moveEarlier = (index: number) => {
        const next = [...roles];
        [next[index - 1], next[index]] = [next[index], next[index - 1]];
        onChange(next);
    };

    return (
        <ol id={id}
            aria-label={"Order in which the roles confirm"}
            className={styles.sequence}>
            {roles.map((role, index) => (
                <li key={role}
                    id={`${id}-${role}`}
                    className={styles.step}>
                    <Text id={`${id}-${role}-label`}>{labelOf(role)}</Text>
                    {index > 0 && !disabled && (
                        <Button id={`${id}-${role}-earlier`}
                                appearance={"subtle"}
                                shape={"circular"}
                                size={"small"}
                                icon={<ArrowUpIcon/>}
                                aria-label={`Move ${labelOf(role)} earlier`}
                                onClick={() => moveEarlier(index)}/>
                    )}
                </li>
            ))}
        </ol>
    );
};

export default ConfirmationRoleSequence;
