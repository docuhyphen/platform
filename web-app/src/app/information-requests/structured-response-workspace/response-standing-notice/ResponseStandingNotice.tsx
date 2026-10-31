import {Button, Text} from "@fluentui/react-components";
import {InformationRequestResponseDto} from "../../../models/models.tsx";
import {responseEditLockLabels} from "../../shared/informationRequestLabels.ts";
import {useResponseStandingNoticeStyles} from "./ResponseStandingNoticeStyles.tsx";

interface Props {
    elementId: string;
    response: InformationRequestResponseDto;
    kept: boolean;
    onKeep: () => void;
}

const RECONFIRM_TEXT = "This request changed after you answered. Check this answer, then change it or keep it as it is.";

const KEPT_TEXT = "You are keeping this answer as it is. It is saved with your other changes.";

const ResponseStandingNotice = ({elementId, response, kept, onKeep}: Props) => {
    const styles = useResponseStandingNoticeStyles();

    if (response.editLock) {
        return (
            <Text id={`information-request-response-lock-${elementId}`}
                  size={200}
                  className={styles.note}>
                {responseEditLockLabels[response.editLock]}
            </Text>
        );
    }
    if (!response.reconfirmationRequired) return null;

    return (
        <div id={`information-request-response-reconfirm-${elementId}`}
             className={styles.reconfirm}>
            <Text id={`information-request-response-reconfirm-text-${elementId}`}
                  size={200}
                  className={styles.note}>
                {kept ? KEPT_TEXT : RECONFIRM_TEXT}
            </Text>
            {!kept && (
                <Button id={`information-request-response-keep-${elementId}`}
                        appearance={"secondary"}
                        shape={"circular"}
                        size={"small"}
                        onClick={onKeep}>
                    Keep this answer
                </Button>
            )}
        </div>
    );
};

export default ResponseStandingNotice;
