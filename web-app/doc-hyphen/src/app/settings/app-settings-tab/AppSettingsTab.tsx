import {Divider, Switch} from "@fluentui/react-components";
import {useAppSettingsTabStyles} from "./AppSettingsTabStyles.tsx";

const AppSettingsTab = () =>
{
    const styles = useAppSettingsTabStyles();

    return <>
        <div className={styles.container}>

            <Switch
                label="Automatically preview documents"
            />
            <Divider appearance={"brand"}
                     alignContent={"start"}>
                Notifications
            </Divider>

            <Switch
                label="Get notifications on Sharing Session Initiation"
            />

            <Switch
                label="Get notifications on Sharing Session Accepted"
            />

            <Switch
                label="Get notifications on Sharing Session Declined"
            />

            <Switch
                label="Get notifications on Sharing Session End"
            />

            <Switch
                label="Get notifications on document notes/comments"
            />

            <Switch
                label="Get notifications on document deletions"
            />

            <Switch
                label="Get notifications on document additions"
            />

            <Switch
                label="Get notifications on document upload"
            />
        </div>
    </>
}

export default AppSettingsTab;