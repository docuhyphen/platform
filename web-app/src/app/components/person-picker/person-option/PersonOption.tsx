import {Persona} from "@fluentui/react-components";
import {getPersonName, PersonPickerItem} from "../personPickerTypes.ts";
import {useAvatarUrl} from "../../hooks/useAvatarUrl.ts";
import {usePersonOptionStyles} from "./PersonOptionStyles.tsx";

interface Props
{
    id: string;
    size?: 'extra-small' | 'small' | 'medium' | 'large' | 'extra-large' | 'huge';
    person: PersonPickerItem;
}

const PersonOption = ({id, person, size}: Props) =>
{
    const styles = usePersonOptionStyles();
    const name = getPersonName(person);
    const avatarUrl = useAvatarUrl(person.avatarUrl);

    return (
        <Persona
            id={id}
            className={styles.persona}
            name={name}
            secondaryText={person.email}
            size={size || "small"}
            avatar={avatarUrl ? {image: {src: avatarUrl}} : undefined}
        />
    );
};

export default PersonOption;
