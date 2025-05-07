import {Button, Switch} from "@fluentui/react-components";
import * as React from "react";
import {PersonAddIcon} from "../../components/IconBundles.tsx";
import {useOrganizationPeopleTabStyles} from "./OrganizationPeopleTabStyles.tsx";

const OrganizationPeopleTab = () =>
{

    const styles = useOrganizationPeopleTabStyles()

    return <div className={styles.container}>

        <div>
            <Button icon={<PersonAddIcon/>}
                    shape={"circular"}>
                Add Person
            </Button>
        </div>

        <Switch
            label="Allow non admins to update their profile"
        />

        <Switch
            label="Allow non admins to update their email"
        />
    </div>
}

export default OrganizationPeopleTab;