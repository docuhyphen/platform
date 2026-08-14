import {InfoLabel} from "@fluentui/react-components";

const PasswordRequirementsInfo = () => (
    <InfoLabel
        infoButton={{
            popover: {
                positioning: "below-end",
            },
        }}
        info={<>
            <strong>Password requirements</strong>
            <ul>
                <li>Must be at least 12 characters long</li>
                <li>Must not exceed 128 characters</li>
                <li>Must contain at least one uppercase letter</li>
                <li>Must contain at least one lowercase letter</li>
                <li>Must contain at least one digit</li>
                <li>Must contain at least one special character</li>
            </ul>
        </>}
    />
);

export default PasswordRequirementsInfo;
