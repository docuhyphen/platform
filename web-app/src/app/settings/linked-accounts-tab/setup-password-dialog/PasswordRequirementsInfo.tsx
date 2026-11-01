import {InfoLabel} from "@fluentui/react-components";

const PasswordRequirementsInfo = () => (
    <InfoLabel
        id={"password-requirements-info-info-label-1"}
        infoButton={{
            popover: {
                positioning: "below-end",
            },
        }}
        info={<>
            <strong id={"password-requirements-info-strong-1"}>Password requirements</strong>
            <ul id={"password-requirements-info-ul-1"}>
                <li id={"password-requirements-info-li-1"}>Must be at least 12 characters long</li>
                <li id={"password-requirements-info-li-2"}>Must not exceed 128 characters</li>
                <li id={"password-requirements-info-li-3"}>Must contain at least one uppercase letter</li>
                <li id={"password-requirements-info-li-4"}>Must contain at least one lowercase letter</li>
                <li id={"password-requirements-info-li-5"}>Must contain at least one digit</li>
                <li id={"password-requirements-info-li-6"}>Must contain at least one special character</li>
            </ul>
        </>}
    />
);

export default PasswordRequirementsInfo;
