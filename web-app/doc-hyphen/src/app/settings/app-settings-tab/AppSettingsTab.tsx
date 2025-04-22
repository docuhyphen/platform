import {Text} from "@fluentui/react-components";

const AppSettingsTab = () =>
{
    return <>
        <div><Text> App Settings Tab</Text>
            <p>
                Automatically preview documents when they are uploaded
            </p>
            <h1>Notifications</h1>
            <p>Get notifications on document comments</p>
            <p>Get notifications on document upload</p>
        </div>
    </>
}

export default AppSettingsTab;