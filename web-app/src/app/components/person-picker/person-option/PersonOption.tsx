import {Persona} from "@fluentui/react-components";
import {getPersonName, PersonPickerItem} from "../personPickerTypes.ts";
import {usePersonOptionStyles} from "./PersonOptionStyles.tsx";

interface Props
{
    id: string;
    person: PersonPickerItem;
}

const PersonOption = ({id, person}: Props) =>
{
    const styles = usePersonOptionStyles();
    const name = getPersonName(person);

    return (
        <Persona
            id={id}
            className={styles.persona}
            name={name}
            secondaryText={person.email}
            size="small"
            avatar={person.avatarUrl ? {image: {src: person.avatarUrl}} : undefined}
        />
    );
};

export default PersonOption;
